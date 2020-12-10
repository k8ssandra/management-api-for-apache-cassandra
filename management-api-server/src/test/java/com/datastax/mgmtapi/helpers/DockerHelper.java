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
import com.github.dockerjava.api.command.CreateContainerResponse;
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
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import org.apache.http.HttpStatus;


public class DockerHelper
{
    private static Logger logger = LoggerFactory.getLogger(DockerHelper.class);

    // Keep track of Docker images built during test runs
    private static final Set<String> IMAGE_NAMES = new HashSet<>();

    // Cleanup hook to remove Docker images built for tests
    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (!Boolean.getBoolean("skip_test_docker_image_cleanup")) {
                    logger.info("Cleaning up test Docker images");
                    DockerClient dockerClient = DockerClientBuilder.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder().build()).build();
                    for (String imageName : IMAGE_NAMES) {
                        Image image = searchImages(imageName, dockerClient);
                        if (image != null) {
                            try {
                                dockerClient.removeImageCmd(image.getId()).exec();
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
    private DockerClientConfig config;
    private DockerClient dockerClient;
    private String container;
    private File dataDir;

    public DockerHelper(File dataDir) {
        this.config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        this.dockerClient = DockerClientBuilder.getInstance(config).build();
        this.dataDir = dataDir;
    }

    public String getIpAddressOfContainer()
    {
        return dockerClient.inspectContainerCmd(container).exec().getNetworkSettings().getIpAddress();
    }

    public void startManagementAPI(String version, List<String> envVars)
    {
        File baseDir = new File(System.getProperty("dockerFileRoot","."));
        File dockerFile;
        String target;
        boolean useBuildx;

        if ("3_11".equals(version))
        {
            dockerFile = Paths.get(baseDir.getPath(), "Dockerfile-oss").toFile();
            target = "oss311";
            useBuildx = true;
        }
        else
        {
            dockerFile = Paths.get(baseDir.getPath(), "Dockerfile-" + version).toFile();
            target = null;
            useBuildx = false;
        }

        if (!dockerFile.exists())
            throw new RuntimeException("Missing " + dockerFile.getAbsolutePath());

        String name = "mgmtapi";
        List<Integer> ports = Arrays.asList(9042, 8080);
        List<String> volumeDescList = Arrays.asList(dataDir.getAbsolutePath() + ":/var/log/cassandra");
        List<String> envList = Lists.newArrayList("MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M");
        List<String> cmdList = Lists.newArrayList("mgmtapi");

        if (envVars != null)
            envList.addAll(envVars);

        this.container = startDocker(dockerFile, baseDir, target, name, ports, volumeDescList, envList, cmdList, useBuildx);

        waitForPort("localhost",8080, Duration.ofMillis(50000), logger, false);
    }

    public String runCommand(String... commandAndArgs)
    {
        if (container == null)
            throw new IllegalStateException("Container not started");

        String execId = dockerClient.execCreateCmd(container).withCmd(commandAndArgs).withAttachStderr(true).withAttachStdout(true).exec().getId();
        dockerClient.execStartCmd(execId).exec(null);

        return execId;
    }

    public void tailSystemLog(int numberOfLines)
    {
        if (container == null)
            throw new IllegalStateException("Container not started");

        String execId = dockerClient.execCreateCmd(container).withTty(true).withCmd("tail", "-n " + numberOfLines, "/var/log/cassandra/system.log").withAttachStderr(true).withAttachStdout(true).exec().getId();
        dockerClient.execStartCmd(execId).withTty(true).exec(new ExecStartResultCallback(System.out, System.err) {});
    }

    public void waitTillFinished(String execId)
    {
        InspectExecResponse r = dockerClient.inspectExecCmd(execId).exec();

        while (r.isRunning())
        {
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            logger.info("SLEEPING");
        }

        if (r.getExitCode() != null && r.getExitCode() != 0)
            throw new RuntimeException("Process error code " + r.getExitCode());

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

    private void buildImageWithBuildx(File dockerFile, File baseDir, String target, String name) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "buildx", "build",
            "--load",
            "--progress", "plain",
            "--tag", name,
            "--file", dockerFile.getPath(),
            "--target", target,
            "--platform", "linux/amd64",
            baseDir.getPath());

        Process p = pb.inheritIO().start();
        int exitCode = p.waitFor();

        if (exitCode != 0)
        {
            throw new Exception("Command '" + String.join(" ", pb.command() + "' return error code: " + exitCode));
        }
    }

    private String startDocker(File dockerFile, File baseDir, String target, String name, List<Integer> ports, List<String> volumeDescList, List<String> envList, List<String> cmdList, boolean useBuildx)
    {
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        listContainersCmd.getFilters().put("name", Arrays.asList(name));
        try
        {
            List<Container> allContainersWithName = listContainersCmd.exec();
            for (Container namedContainer : allContainersWithName)
            {
                String id = namedContainer.getId();
                logger.info("Removing container: " + id);
                dockerClient.stopContainerCmd(id).exec();
                dockerClient.removeContainerCmd(id).exec();
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
        final String imageName = String.format("%s-%s-test", name, dockerFile.getName()).toLowerCase();
        Image image = searchImages(imageName, dockerClient);
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

            logger.info(String.format("Building container: name=%s, Dockerfile=%s, image name=%s", name, dockerFile.getPath(), imageName));
            if (useBuildx)
            {
                try
                {
                    buildImageWithBuildx(dockerFile, baseDir, target, imageName);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    logger.error("Unable to build image");
                }
            }
            else
            {
                dockerClient.buildImageCmd()
                    .withBaseDirectory(baseDir)
                    .withDockerfile(dockerFile)
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
        containerResponse = dockerClient.createContainerCmd(imageName)
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


        dockerClient.startContainerCmd(containerResponse.getId()).exec();
        dockerClient.logContainerCmd(containerResponse.getId()).withStdOut(true).withStdErr(true).withFollowStream(true).withTailAll().exec(new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item)
            {
                System.out.print(new String(item.getPayload()));
            }
        });

        return containerResponse.getId();
    }

    private Container searchContainer(String name)
    {
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd().withStatusFilter(Collections.singletonList("running"));
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

    private static Image searchImages(String imageName, DockerClient dockerClient)
    {
        ListImagesCmd listImagesCmd = dockerClient.listImagesCmd();
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
            dockerClient.stopContainerCmd(container).exec();
            dockerClient.removeContainerCmd(container).exec();
            container = null;
        }
    }
}
