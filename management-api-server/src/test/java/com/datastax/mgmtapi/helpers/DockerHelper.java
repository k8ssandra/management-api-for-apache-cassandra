/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.helpers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback.Adapter;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerHelper {
  private static Logger logger = LoggerFactory.getLogger(DockerHelper.class);

  public static final String CONTAINER_NAME = "mgmtapi";
  // Keep track of Docker images built during test runs
  private static final Set<String> IMAGE_NAMES = new HashSet<>();

  private static final DockerClientConfig CONFIG =
      DefaultDockerClientConfig.createDefaultConfigBuilder().build();

  private static final DockerClient DOCKER_CLIENT =
      DockerClientBuilder.getInstance(CONFIG)
          .withDockerHttpClient(
              new ZerodepDockerHttpClient.Builder()
                  .dockerHost(CONFIG.getDockerHost())
                  .sslConfig(CONFIG.getSSLConfig())
                  .maxConnections(100)
                  .build())
          .build();;

  // Cleanup hook to remove Docker images built for tests
  static {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                if (!Boolean.getBoolean("skip_test_docker_image_cleanup")) {
                  logger.info("Cleaning up test Docker images");
                  for (String imageName : IMAGE_NAMES) {
                    Image image = searchImages(imageName);
                    if (image != null) {
                      try {
                        DOCKER_CLIENT.removeImageCmd(image.getId()).exec();
                      } catch (Throwable e) {
                        logger.info(
                            String.format("Removing image %s did not complete cleanly", imageName));
                      }
                    }
                  }
                } else {
                  logger.info("Skipping test Docker image cleanup");
                }
              }
            });
  }

  private String container;
  private final File dataDir;

  public DockerHelper(File dataDir) {
    this.dataDir = dataDir;
  }

  public String getIpAddressOfContainer() {
    return DOCKER_CLIENT.inspectContainerCmd(container).exec().getNetworkSettings().getIpAddress();
  }

  public void startManagementAPI(
      String version, List<String> envVars, String user, List<String> buildVars) {
    DockerBuildConfig config = DockerBuildConfig.getConfig(version, envVars, user, buildVars);
    if (!config.dockerFile.exists())
      throw new RuntimeException("Missing " + config.dockerFile.getAbsolutePath());

    this.container = startDocker(config);

    // see if the default listen port has been overridden
    int listenPort = getListenPortFromEnv(envVars);
    waitForPort("localhost", listenPort, Duration.ofMillis(50000), logger, false);
  }

  public void runCommand(String... commandAndArgs) throws IOException, InterruptedException {
    if (container == null) throw new IllegalStateException("Container not started");
    // prefix the command arguments with "docker exec mgmtapi"
    final ProcessBuilder pb = new ProcessBuilder("docker", "exec", CONTAINER_NAME);
    for (String arg : commandAndArgs) {
      pb.command().add(arg);
    }
    final Process process = pb.start();
    final int exitCode = process.waitFor();
    if (exitCode != 0) {
      logger.error("Command had a non-zero exit code: " + exitCode);
      new BufferedReader(new InputStreamReader(process.getInputStream()))
          .lines()
          .forEach(
              line -> {
                logger.error("Command output stream: " + line);
              });
      new BufferedReader(new InputStreamReader(process.getErrorStream()))
          .lines()
          .forEach(
              line -> {
                logger.error("Command error stream: " + line);
              });
      throw new IOException("Command was not successful: " + Arrays.toString(commandAndArgs));
    }
  }

  public void tailSystemLog(int numberOfLines) {
    if (container == null) throw new IllegalStateException("Container not started");

    String execId =
        DOCKER_CLIENT
            .execCreateCmd(container)
            .withTty(true)
            .withCmd("tail", "-n " + numberOfLines, "/var/log/cassandra/system.log")
            .withAttachStderr(true)
            .withAttachStdout(true)
            .exec()
            .getId();
    try {
      DOCKER_CLIENT
          .execStartCmd(execId)
          .withTty(true)
          .exec(
              new Adapter<Frame>() {
                @Override
                public void onNext(Frame item) {
                  System.out.print(new String(item.getPayload()));
                }
              })
          .awaitCompletion();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.warn("tail system.log interrupted");
    }
  }

  public static boolean waitForPort(
      String hostname, int port, Duration timeout, Logger logger, boolean quiet) {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();

    final String basePath = String.format("http://%s:%d", hostname, port);

    while (System.nanoTime() < deadlineNanos) {
      try {
        NettyHttpClient client = new NettyHttpClient(new URL(basePath));

        // Verify liveness
        boolean live =
            client
                .get(URI.create(basePath + "/api/v0/probes/liveness").toURL())
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
                .join();

        if (live) return live;

      } catch (Throwable t) {
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
      }
    }

    // The port never opened
    if (!quiet) {
      logger.warn("Failed to connect to {}:{} after {} sec", hostname, port, timeout.getSeconds());
    }

    return false;
  }

  public boolean started() {
    return container != null;
  }

  private void buildImageWithBuildx(DockerBuildConfig config, String name) throws Exception {
    ProcessBuilder pb =
        new ProcessBuilder("docker", "buildx", "build", "--load", "--progress", "plain");
    // add extras build-args, if we have any
    if (!config.buildVars.isEmpty()) {
      for (String buildArg : config.buildVars) {
        pb.command().add("--build-arg");
        pb.command().add(buildArg);
      }
    }
    pb.command().add("--tag");
    pb.command().add(name);
    pb.command().add("--file");
    pb.command().add(config.dockerFile.getPath());
    pb.command().add("--target");
    pb.command().add(config.target);
    pb.command().add("--platform");
    pb.command().add("linux/amd64");
    pb.command().add(config.baseDir.getPath());

    Process p = pb.inheritIO().start();
    int exitCode = p.waitFor();

    if (exitCode != 0) {
      throw new Exception(
          "Command '" + String.join(" ", pb.command() + "' return error code: " + exitCode));
    }
  }

  public void removeExistingCntainers() {
    ListContainersCmd listContainersCmd = DOCKER_CLIENT.listContainersCmd();
    listContainersCmd.getFilters().put("name", Arrays.asList(CONTAINER_NAME));
    try {
      List<Container> allContainersWithName = listContainersCmd.exec();
      for (Container namedContainer : allContainersWithName) {
        String id = namedContainer.getId();
        logger.info("Removing container: " + id);
        DOCKER_CLIENT.stopContainerCmd(id).exec();
        DOCKER_CLIENT.removeContainerCmd(id).exec();
      }
    } catch (Exception e) {
      e.printStackTrace();
      logger.error("Unable to contact docker, make sure docker is up and try again.");
      logger.error("If docker is installed make sure this user has access to the docker group.");
      logger.error("$ sudo gpasswd -a ${USER} docker && newgrp docker");
      System.exit(1);
    }
  }

  private String startDocker(DockerBuildConfig config) {
    Container containerId = searchContainer(CONTAINER_NAME);
    if (containerId != null) {
      return containerId.getId();
    }

    // see if we have the image already built
    final String imageName =
        String.format("%s-%s-test", CONTAINER_NAME, config.dockerFile.getName()).toLowerCase();
    Image image = searchImages(imageName);
    if (image == null) {
      logger.info(
          String.format(
              "Building container: name=%s, Dockerfile=%s, image name=%s",
              CONTAINER_NAME, config.dockerFile.getPath(), imageName));
      try {
        buildImageWithBuildx(config, imageName);
      } catch (Exception e) {
        e.printStackTrace();
        logger.error("Unable to build image");
      }
      logger.info(
          String.format("Adding image named %s to set of images to be cleaned up", imageName));
      IMAGE_NAMES.add(imageName);
    }

    List<ExposedPort> tcpPorts = new ArrayList<>();
    List<PortBinding> portBindings = new ArrayList<>();
    for (Integer port : config.exposedPorts) {
      ExposedPort tcpPort = ExposedPort.tcp(port);
      Ports.Binding binding = new Ports.Binding("0.0.0.0", String.valueOf(port));
      PortBinding pb = new PortBinding(binding, tcpPort);

      tcpPorts.add(tcpPort);
      portBindings.add(pb);
    }

    CreateContainerResponse containerResponse;

    logger.warn(
        "Binding a local temp directory to /var/log/cassandra can cause permissions issues on startup. Skipping volume bindings.");
    containerResponse =
        DOCKER_CLIENT
            .createContainerCmd(imageName)
            .withEnv(config.envList)
            .withExposedPorts(tcpPorts)
            .withHostConfig(
                new HostConfig().withPortBindings(portBindings).withPublishAllPorts(true)
                // don't bind /var/log/cassandra, it causes permissions issues with startup
                // .withBinds(volumeBindList)
                )
            .withName(CONTAINER_NAME)
            .withUser(config.user)
            .exec();

    DOCKER_CLIENT.startContainerCmd(containerResponse.getId()).exec();
    DOCKER_CLIENT
        .logContainerCmd(containerResponse.getId())
        .withStdOut(true)
        .withStdErr(true)
        .withFollowStream(true)
        .withTailAll()
        .exec(
            new Adapter<Frame>() {
              @Override
              public void onNext(Frame item) {
                System.out.print(new String(item.getPayload()));
              }

              @Override
              public void onStart(Closeable stream) {
                System.out.println("Starting container " + CONTAINER_NAME);
              }
            });

    return containerResponse.getId();
  }

  private Container searchContainer(String name) {
    ListContainersCmd listContainersCmd =
        DOCKER_CLIENT.listContainersCmd().withStatusFilter(Collections.singletonList("running"));
    listContainersCmd.getFilters().put("name", Arrays.asList(name));
    List<Container> runningContainers = null;
    try {
      runningContainers = listContainersCmd.exec();
    } catch (Exception e) {
      e.printStackTrace();
      logger.error("Unable to contact docker, make sure docker is up and try again.");
      System.exit(1);
    }

    if (runningContainers.size() >= 1) {
      // Container test = runningContainers.get(0);
      logger.info(String.format("The container %s is already running", name));

      return runningContainers.get(0);
    }
    return null;
  }

  private static Image searchImages(String imageName) {
    ListImagesCmd listImagesCmd = DOCKER_CLIENT.listImagesCmd();
    List<Image> images = null;
    logger.info(String.format("Searching for image named %s", imageName));
    try {
      images = listImagesCmd.exec();
      if (!images.isEmpty()) {
        Iterator<Image> it = images.iterator();
        while (it.hasNext()) {
          Image image = it.next();
          String[] tags = image.getRepoTags();
          if (tags == null) {
            logger.warn(String.format("Image has NULL tags: %s", image.getId()));
          } else {
            for (int i = 0; i < tags.length; ++i) {
              if (tags[i].startsWith(imageName)) {
                logger.info(String.format("Found an image named %s", imageName));
                return image;
              }
            }
          }
        }
      }
    } catch (Exception e) {
      logger.warn("Failed to fetch images", e);
    }
    logger.info(String.format("No image named %s found", imageName));
    return null;
  }

  public void stopManagementAPI() {
    if (container != null) {
      DOCKER_CLIENT.stopContainerCmd(container).exec();
      DOCKER_CLIENT.removeContainerCmd(container).exec();
      container = null;
    }
  }

  private static class DockerBuildConfig {
    final File baseDir = new File(System.getProperty("dockerFileRoot", "."));
    File dockerFile;
    String target;
    List<Integer> exposedPorts;
    List<String> envList;
    String user;
    List<String> buildVars = Lists.newArrayList();

    static DockerBuildConfig getConfig(
        String version, List<String> envVars, String user, List<String> buildVars) {
      DockerBuildConfig config = new DockerBuildConfig();
      switch (version) {
        case "3_11":
          config.dockerFile = Paths.get(config.baseDir.getPath(), "Dockerfile-oss").toFile();
          config.target = "oss311";
          config.envList = Lists.newArrayList("MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M");
          break;
        case "3_11_ubi":
          config.dockerFile = Paths.get(config.baseDir.getPath(), "Dockerfile-oss.ubi8").toFile();
          config.target = "oss311";
          config.envList = Lists.newArrayList("MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M");
          break;
        case "4_0":
          config.dockerFile = Paths.get(config.baseDir.getPath(), "Dockerfile-4_0").toFile();
          config.target = "oss40";
          config.envList = Lists.newArrayList("MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M");
          break;
        case "4_0_ubi":
          config.dockerFile = Paths.get(config.baseDir.getPath(), "Dockerfile-4_0.ubi8").toFile();
          config.target = "oss40";
          config.envList = Lists.newArrayList("MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M");
          break;
        case "4_1":
          config.dockerFile = Paths.get(config.baseDir.getPath(), "Dockerfile-4_1").toFile();
          config.target = "oss41";
          config.envList = Lists.newArrayList("MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M");
          break;
        case "4_1_ubi":
          config.dockerFile = Paths.get(config.baseDir.getPath(), "Dockerfile-4_1.ubi8").toFile();
          config.target = "oss41";
          config.envList = Lists.newArrayList("MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M");
          break;
        case "5_0_ubi":
          config.dockerFile = Paths.get(config.baseDir.getPath(), "Dockerfile-5_0.ubi8").toFile();
          config.target = "oss50";
          config.envList =
              Lists.newArrayList(
                  "MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M", "MGMT_API_DISABLE_MCAC=true");
          break;
        case "trunk":
          config.dockerFile =
              Paths.get(config.baseDir.getPath(), "cassandra-trunk", "Dockerfile.ubi8").toFile();
          config.target = "cass-trunk";
          config.envList = Lists.newArrayList("MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M");
          break;
        case "dse-68":
          config.dockerFile =
              Paths.get(config.baseDir.getPath(), "dse", "Dockerfile-dse68.jdk8").toFile();
          config.target = "dse";
          config.envList =
              Lists.newArrayList(
                  "MAX_HEAP_SIZE=500M",
                  "HEAP_NEWSIZE=100M",
                  "DS_LICENSE=accept",
                  "USE_MGMT_API=true");
          break;
        case "dse-68_ubi":
          config.dockerFile =
              Paths.get(config.baseDir.getPath(), "dse", "Dockerfile-dse68.ubi8").toFile();
          config.target = "dse";
          config.envList =
              Lists.newArrayList(
                  "MAX_HEAP_SIZE=500M",
                  "HEAP_NEWSIZE=100M",
                  "DS_LICENSE=accept",
                  "USE_MGMT_API=true");
          break;
        case "dse-69":
          config.dockerFile =
              Paths.get(config.baseDir.getPath(), "dse", "Dockerfile-dse69.jdk11").toFile();
          config.target = "dse";
          config.envList =
              Lists.newArrayList(
                  "MAX_HEAP_SIZE=500M",
                  "HEAP_NEWSIZE=100M",
                  "DS_LICENSE=accept",
                  "USE_MGMT_API=true",
                  "MGMT_API_DISABLE_MCAC=true");
          break;
        case "dse-69_ubi":
          config.dockerFile =
              Paths.get(config.baseDir.getPath(), "dse", "Dockerfile-dse69.ubi8").toFile();
          config.target = "dse";
          config.envList =
              Lists.newArrayList(
                  "MAX_HEAP_SIZE=500M",
                  "HEAP_NEWSIZE=100M",
                  "DS_LICENSE=accept",
                  "USE_MGMT_API=true",
                  "MGMT_API_DISABLE_MCAC=true");
          break;
        default:
          throw new RuntimeException("Unsupported Cassandra version: " + version);
      }
      if (envVars != null) {
        config.envList.addAll(envVars);
        // add exposed ports
        config.exposedPorts = Arrays.asList(9042, 9000, getListenPortFromEnv(envVars));
      }
      config.user = user;
      config.buildVars.addAll(buildVars);
      return config;
    }
  }

  static int getListenPortFromEnv(List<String> envVars) {
    for (String envVar : envVars) {
      if (envVar.startsWith("MGMT_API_LISTEN_TCP_PORT")) {
        // we have a specified port
        int listenPort =
            Integer.parseInt(envVar.substring("MGMT_API_LISTEN_TCP_PORT".length() + 1));
        return listenPort;
      }
    }
    // use default
    return 8080;
  }
}
