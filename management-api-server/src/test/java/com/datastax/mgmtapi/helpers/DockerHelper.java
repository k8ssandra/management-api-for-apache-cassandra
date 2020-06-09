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
import java.util.List;
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
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
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
    private DockerClientConfig config;
    private DockerClient dockerClient;
    private String container;
    private File dataDir;
    private Logger logger = LoggerFactory.getLogger(DockerHelper.class);

    public DockerHelper(File dataDir) {
        this.config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        this.dockerClient = DockerClientBuilder.getInstance(config).build();
        this.dataDir = dataDir;
    }

    public void startManagementAPI(String version, List<String> envVars)
    {
        File baseDir = new File(System.getProperty("dockerFileRoot","."));
        File dockerFile = Paths.get(baseDir.getPath(), "Dockerfile-" + version).toFile();
        if (!dockerFile.exists())
            throw new RuntimeException("Missing " + dockerFile.getAbsolutePath());

        String name = "mgmtapi";
        List<Integer> ports = Arrays.asList(9042, 8080);
        List<String> volumeDescList = Arrays.asList(dataDir.getAbsolutePath() + ":/var/log/cassandra");
        List<String> envList = Lists.newArrayList("MAX_HEAP_SIZE=500M", "HEAP_NEWSIZE=100M");
        List<String> cmdList = Lists.newArrayList("mgmtapi");

        if (envVars != null)
            envList.addAll(envVars);

        this.container = startDocker(dockerFile, baseDir, name, ports, volumeDescList, envList, cmdList);

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

    private String startDocker(File dockerFile, File baseDir, String name, List<Integer> ports, List<String> volumeDescList, List<String> envList, List<String> cmdList)
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

        BuildImageResultCallback callback = new BuildImageResultCallback()
        {
            @Override
            public void onNext(BuildResponseItem item)
            {
                //System.out.println("" + item);
                super.onNext(item);
            }
        };

        logger.info("Building container: " + name + " from " + dockerFile);
        dockerClient.buildImageCmd()
                .withBaseDirectory(baseDir)
                .withDockerfile(dockerFile)
                .withTags(Sets.newHashSet(name))
                .exec(callback)
                .awaitImageId();

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

        containerResponse = dockerClient.createContainerCmd(name)
                .withCmd(cmdList)
                .withEnv(envList)
                .withExposedPorts(tcpPorts)
                .withHostConfig(
                        new HostConfig()
                                .withPortBindings(portBindings)
                                .withPublishAllPorts(true)
                                .withBinds(volumeBindList)
                )
                .withName(name)
                .exec();


        dockerClient.startContainerCmd(containerResponse.getId()).exec();
        dockerClient.logContainerCmd(containerResponse.getId()).withStdOut(true).withStdErr(true).withFollowStream(true).withTailAll().exec(new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item)
            {
                logger.info(new String(item.getPayload()));
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

    public void stopManagementAPI()
    {
        if (container != null)
        {
            dockerClient.stopContainerCmd(container).exec();
            dockerClient.removeContainerCmd(container).exec();
        }
    }
}
