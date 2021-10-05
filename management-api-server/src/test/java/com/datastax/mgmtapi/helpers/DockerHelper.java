/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.helpers;

import java.io.File;
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback.Adapter;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.io.Closeable;
import org.apache.http.HttpStatus;


public class DockerHelper
{
    private static Logger logger = LoggerFactory.getLogger(DockerHelper.class);

    // Keep track of Docker images built during test runs
    private static final Set<String> IMAGE_NAMES = new HashSet<>();

    private static final DockerClientConfig CONFIG = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

    private static final DockerClient DOCKER_CLIENT = DockerClientBuilder.getInstance(CONFIG)
                .withDockerHttpClient(
                        new ZerodepDockerHttpClient.Builder()
                                .dockerHost(CONFIG.getDockerHost())
                                .sslConfig(CONFIG.getSSLConfig())
                                .maxConnections(100)
                                .build())
                .build();;

    // Cleanup hook to remove Docker images built for tests
    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
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
                                logger.info(String.format("Removing image %s did not complete cleanly", imageName));
                            }
                        }
                    }
                }
                else
                {
                    logger.info("Skipping test Docker image cleanup");
                }
            }
        });
    }
    private String container;
    private File dataDir;

    public DockerHelper(File dataDir) {
        this.dataDir = dataDir;
    }

    public String getIpAddressOfContainer()
    {
        return DOCKER_CLIENT.inspectContainerCmd(container).exec().getNetworkSettings().getIpAddress();
    }

    public void startManagementAPI(String version, List<String> envVars)
    {
        DockerBuildConfig config = DockerBuildConfig.getConfig(version);
        if (!config.dockerFile.exists())
            throw new RuntimeException("Missing " + config.dockerFile.getAbsolutePath());

        String name = "mgmtapi";
        List<Integer> ports = Arrays.asList(9042, 8080);
        List<String> volumeDescList = Arrays.asList(dataDir.getAbsolutePath() + ":/var/log/cassandra");
        List<String> envList = Lists.newArrayList("MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M");
        List<String> cmdList = Lists.newArrayList("mgmtapi");

        if (envVars != null)
            envList.addAll(envVars);

        this.container = startDocker(config, name, ports, volumeDescList, envList, cmdList);

        waitForPort("localhost",8080, Duration.ofMillis(50000), logger, false);
    }

    public String runCommand(String... commandAndArgs)
    {
        if (container == null)
            throw new IllegalStateException("Container not started");

        String execId = DOCKER_CLIENT.execCreateCmd(container).withCmd(commandAndArgs).withAttachStderr(true).withAttachStdout(true).exec().getId();
        DOCKER_CLIENT.execStartCmd(execId).exec(new Adapter<Frame>());

        return execId;
    }

    public void tailSystemLog(int numberOfLines)
    {
        if (container == null)
            throw new IllegalStateException("Container not started");

        String execId = DOCKER_CLIENT.execCreateCmd(container).withTty(true).withCmd("tail", "-n " + numberOfLines, "/var/log/cassandra/system.log").withAttachStderr(true).withAttachStdout(true).exec().getId();
        try
        {
            DOCKER_CLIENT.execStartCmd(execId).withTty(true).exec(new Adapter<Frame>() {
                @Override
                public void onNext(Frame item) {
                    System.out.print(new String(item.getPayload()));
                }
            }).awaitCompletion();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            logger.warn("tail system.log interrupted");
        }
    }

    public void waitTillFinished(String execId)
    {
        InspectExecCmd cmd = DOCKER_CLIENT.inspectExecCmd(execId);
        InspectExecResponse r = cmd.exec();
        while (r.isRunning())
        {
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            logger.info("SLEEPING");
            r = cmd.exec();
        }

        if (r.getExitCodeLong() != null && r.getExitCodeLong() != 0l)
            throw new RuntimeException("Process error code " + r.getExitCodeLong());

        logger.info("PROCESS finished!");
    }

    public static boolean waitForPort(String hostname, int port, Duration timeout, Logger logger, boolean quiet)
    {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();

        while(System.nanoTime() < deadlineNanos)
        {
            try
            {
                NettyHttpClient client = new NettyHttpClient(new URL("http://" + hostname + ":" + port));


                //Verify liveness
                boolean live = client.get(URI.create("http://localhost/api/v0/probes/liveness").toURL())
                        .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

                if (live)
                    return live;

            }
            catch (Throwable t)
            {
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            }
        }

        //The port never opened
        if (!quiet)
        {
            logger.warn("Failed to connect to {}:{} after {} sec", hostname, port, timeout.getSeconds());
        }

        return false;
    }

    public boolean started()
    {
        return container != null;
    }

    private void buildImageWithBuildx(DockerBuildConfig config, String name) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "buildx", "build",
            "--load",
            "--progress", "plain",
            "--tag", name,
            "--file", config.dockerFile.getPath(),
            "--target", config.target,
            "--platform", "linux/amd64",
            config.baseDir.getPath());

        Process p = pb.inheritIO().start();
        int exitCode = p.waitFor();

        if (exitCode != 0)
        {
            throw new Exception("Command '" + String.join(" ", pb.command() + "' return error code: " + exitCode));
        }
    }

    private String startDocker(DockerBuildConfig config, String name, List<Integer> ports, List<String> volumeDescList, List<String> envList, List<String> cmdList)
    {
        ListContainersCmd listContainersCmd = DOCKER_CLIENT.listContainersCmd();
        listContainersCmd.getFilters().put("name", Arrays.asList(name));
        try
        {
            List<Container> allContainersWithName = listContainersCmd.exec();
            for (Container namedContainer : allContainersWithName)
            {
                String id = namedContainer.getId();
                logger.info("Removing container: " + id);
                DOCKER_CLIENT.stopContainerCmd(id).exec();
                DOCKER_CLIENT.removeContainerCmd(id).exec();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            logger.error("Unable to contact docker, make sure docker is up and try again.");
            logger.error("If docker is installed make sure this user has access to the docker group.");
            logger.error("$ sudo gpasswd -a ${USER} docker && newgrp docker");
            System.exit(1);
        }

        Container containerId = searchContainer(name);
        if (containerId != null)
        {
            return containerId.getId();
        }

        // see if we have the image already built
        final String imageName = String.format("%s-%s-test", name, config.dockerFile.getName()).toLowerCase();
        Image image = searchImages(imageName);
        if (image == null)
        {
            BuildImageResultCallback callback = new BuildImageResultCallback()
            {
                @Override
                public void onNext(BuildResponseItem item)
                {
                    String stream = item.getStream();
                    if (stream != null && !stream.equals("null"))
                        System.out.print(item.getStream());
                    super.onNext(item);
                }
            };

            logger.info(String.format("Building container: name=%s, Dockerfile=%s, image name=%s", name, config.dockerFile.getPath(), imageName));
            if (config.useBuildx)
            {
                try
                {
                    buildImageWithBuildx(config, imageName);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    logger.error("Unable to build image");
                }
            }
            else
            {
                DOCKER_CLIENT.buildImageCmd()
                    .withBaseDirectory(config.baseDir)
                    .withDockerfile(config.dockerFile)
                    .withTags(Sets.newHashSet(imageName))
                    .exec(callback)
                    .awaitImageId();
            }
            logger.info(String.format("Adding image named %s to set of images to be cleaned up", imageName));
            IMAGE_NAMES.add(imageName);
        }

        List<ExposedPort> tcpPorts = new ArrayList<>();
        List<PortBinding> portBindings = new ArrayList<>();
        for (Integer port : ports)
        {
            ExposedPort tcpPort = ExposedPort.tcp(port);
            Ports.Binding binding = new Ports.Binding("0.0.0.0", String.valueOf(port));
            PortBinding pb = new PortBinding(binding, tcpPort);

            tcpPorts.add(tcpPort);
            portBindings.add(pb);
        }

        List<Volume> volumeList = new ArrayList<>();
        List<Bind> volumeBindList = new ArrayList<>();
        for (String volumeDesc : volumeDescList)
        {
            String volFrom = volumeDesc.split(":")[0];
            String volTo = volumeDesc.split(":")[1];
            Volume vol = new Volume(volTo);
            volumeList.add(vol);
            volumeBindList.add(new Bind(volFrom, vol));
        }

        CreateContainerResponse containerResponse;

        logger.warn("Binding a local temp directory to /var/log/cassandra can cause permissions issues on startup. Skipping volume bindings.");
        containerResponse = DOCKER_CLIENT.createContainerCmd(imageName)
                .withCmd(cmdList)
                .withEnv(envList)
                .withExposedPorts(tcpPorts)
                .withHostConfig(
                        new HostConfig()
                                .withPortBindings(portBindings)
                                .withPublishAllPorts(true)
                                // don't bind /var/log/cassandra, it causes permissions issues with startup
                                //.withBinds(volumeBindList)
                )
                .withName(name)
                .exec();


        DOCKER_CLIENT.startContainerCmd(containerResponse.getId()).exec();
        DOCKER_CLIENT.logContainerCmd(containerResponse.getId()).withStdOut(true).withStdErr(true).withFollowStream(true).withTailAll().exec(new Adapter<Frame>() {
            @Override
            public void onNext(Frame item)
            {
                System.out.print(new String(item.getPayload()));
            }

            @Override
            public void onStart(Closeable stream) {
                System.out.println("Starting container " + name);
            }
        });

        return containerResponse.getId();
    }

    private Container searchContainer(String name)
    {
        ListContainersCmd listContainersCmd = DOCKER_CLIENT.listContainersCmd().withStatusFilter(Collections.singletonList("running"));
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
            //Container test = runningContainers.get(0);
            logger.info(String.format("The container %s is already running", name));

            return runningContainers.get(0);
        }
        return null;
    }

    private static Image searchImages(String imageName)
    {
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
                    if (tags == null)
                    {
                        logger.warn(String.format("Image has NULL tags: %s", image.getId()));
                    }
                    else
                    {
                        for (int i=0; i< tags.length; ++i) {
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

    public void stopManagementAPI()
    {
        if (container != null)
        {
            DOCKER_CLIENT.stopContainerCmd(container).exec();
            DOCKER_CLIENT.removeContainerCmd(container).exec();
            container = null;
        }
    }

    private static class DockerBuildConfig
    {
        static final File baseDir = new File(System.getProperty("dockerFileRoot","."));

        File dockerFile;
        String target = null;
        boolean useBuildx = false;

        static DockerBuildConfig getConfig(String version)
        {
            DockerBuildConfig config = new DockerBuildConfig();
            switch (version) {
              case "3_11" :
                  config.dockerFile = Paths.get(baseDir.getPath(), "Dockerfile-oss").toFile();
                  config.target = "oss311";
                  config.useBuildx = true;
                  break;
              case "4_0" :
                  config.dockerFile = Paths.get(baseDir.getPath(), "Dockerfile-4_0").toFile();
                  config.target = "oss40";
                  config.useBuildx = true;
                  break;
              default : // DSE 6.8
                  config.dockerFile = Paths.get(baseDir.getPath(), "Dockerfile-dse-68").toFile();
                  break;
            }
            return config;
        }
    }
}
