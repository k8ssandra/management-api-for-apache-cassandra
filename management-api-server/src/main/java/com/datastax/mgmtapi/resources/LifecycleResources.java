/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.UnixCmds;
import com.datastax.mgmtapi.UnixSocketCQLAccess;
import com.datastax.mgmtapi.util.ShellUtils;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.shaded.guava.common.collect.Streams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.apache.http.HttpStatus;

import static com.datastax.mgmtapi.ManagementApplication.STATE.STARTED;
import static com.datastax.mgmtapi.ManagementApplication.STATE.STOPPED;

@Path("/api/v0/lifecycle")
public class LifecycleResources
{
    private static final Logger logger = LoggerFactory.getLogger(LifecycleResources.class);
    private final ManagementApplication app;

    static final YAMLMapper yamlMapper = new YAMLMapper();
    static final String PROFILE_PATTERN = "[0-9a-zA-Z\\-_]+";
    static final String IPV4_PATTERN = "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$";
    private ObjectMapper objectMapper = new ObjectMapper();

    public LifecycleResources(ManagementApplication app)
    {
        this.app = app;
    }

    /**
     * Starts a Cassandra/DSE node
     *
     * Handles the following:
     *   - Cassandra pid found and can connect: status 202
     *   - Cassandra pid not found and can not connect: status 201
     *   - Cassandra pid not found but can connect: status 206
     *   - Cassandra pid found but can not connect: status 204
     *
     * @return
     */
    @Path("/start")
    @POST
    @Operation(description = "Starts Cassandra/DSE")
    public synchronized Response startNode(@QueryParam("profile") String profile, @QueryParam("replace_ip") String replaceIp)
    {
        app.setRequestedState(STARTED);

        //Todo we should add a CALL getPid command and compare;
        boolean canConnect;
        try
        {
            CqlSession session = UnixSocketCQLAccess.get(app.dbUnixSocketFile).get();
            session.execute("SELECT * FROM system.peers");

            canConnect = true;
        }
        catch (Throwable t)
        {
            logger.debug("Could not connect:", t);
            canConnect = false;
        }

        try
        {
            Optional<Integer> maybePid = findPid();

            if (maybePid.isPresent())
            {
                return Response.status(canConnect ? HttpStatus.SC_ACCEPTED : HttpStatus.SC_NO_CONTENT).build();
            }
            else if (canConnect)
                return Response.status(HttpStatus.SC_PARTIAL_CONTENT).build();
        }
        catch (Throwable t)
        {
            logger.error("Error checking pid", t);
            return Response.serverError().entity(Entity.text(t.getLocalizedMessage())).build();
        }

        try
        {
            String extra = System.getenv("JVM_EXTRA_OPTS");
            ImmutableMap.Builder<String, String> envArgsBuilder = ImmutableMap.builder();

            if (extra != null)
                envArgsBuilder.put("JVM_EXTRA_OPTS", extra);

            Map<String, String> environment = envArgsBuilder.build();
            StringBuilder extraArgs = new StringBuilder();

            if (profile != null)
            {
                app.setActiveProfile(profile);

                extraArgs.append("-Dcassandra.config=file:///tmp/").append(profile).append("/cassandra.yaml")
                        .append(" -Dcassandra-rackdc.properties=file:///tmp/").append(profile).append("/node-topology.properties");
            }

            if (replaceIp != null)
            {
                if (!replaceIp.matches(IPV4_PATTERN))
                    return Response.serverError().entity(Entity.text("Invalid replace IP passed: " + replaceIp)).build();

                extraArgs.append("-Dcassandra.replace_address_first_boot=").append(replaceIp);
            }

            String cassandraOrDseCommand = app.dbExe.getAbsolutePath();
            if (cassandraOrDseCommand.endsWith("dse"))
            {
                cassandraOrDseCommand += " cassandra";
            }

            //Delete stale file if it exists
            if (app.dbUnixSocketFile.exists())
                FileUtils.deleteQuietly(app.dbUnixSocketFile);

            boolean started = ShellUtils.executeShellWithHandlers(
                    String.format("nohup %s %s -R -Dcassandra.server_process -Dcassandra.skip_default_role_setup=true -Ddb.unix_socket_file=%s %s %s > /var/log/cassandra/stdout.log 2> /var/log/cassandra/stderr.log",
                            profile != null ? "/tmp/" + profile + "/env.sh" : "",
                            cassandraOrDseCommand,
                            app.dbUnixSocketFile.getAbsolutePath(),
                            extraArgs.toString(),
                            String.join(" ", app.dbExtraJvmArgs)),
                    (input, err) -> true,
                    (exitCode, err) -> {
                        logger.error("Error starting Cassandra: {}", err.lines().collect(Collectors.joining("\n")));
                        return false;
                    },
                    environment);

            if (started)
                logger.info("Started Cassandra");
            else
                logger.warn("Error starting Cassandra");

            return Response.status(started ? HttpStatus.SC_CREATED : HttpStatus.SC_METHOD_FAILURE)
                    .entity(started ? "OK\n" : "Error starting Cassandra").build();
        }
        catch (Throwable t)
        {
            return Response.serverError().entity(Entity.text(t.getLocalizedMessage())).build();
        }
    }

    @Path("/stop")
    @POST
    @Operation(description = "Stops Cassandra/DSE. Keeps node from restarting automatically until /start is called")
    public synchronized Response stopNode()
    {
        app.setRequestedState(STOPPED);

        try
        {
            // 30 Seconds
            int tries = 6;
            int sleepSeconds = 5;

            do
            {
                Optional<Integer> maybePid = findPid();

                if (!maybePid.isPresent())
                {
                    logger.info("Cassandra already stopped");
                    return Response.ok("OK\n").build();
                }

                Boolean stopped = ShellUtils.executeShellWithHandlers(
                        String.format("kill %d", maybePid.get()),
                        (input, err) -> true,
                        (exitCode, err) -> false);

                if (!stopped)
                    logger.warn("Killing Cassandra failed");

                Uninterruptibles.sleepUninterruptibly(sleepSeconds, TimeUnit.SECONDS);
            } while (tries-- > 0);


            Optional<Integer> maybePid = findPid();
            if (maybePid.isPresent())
            {
                Boolean stopped = ShellUtils.executeShellWithHandlers(
                        String.format("kill -9 %d", maybePid.get()),
                        (input, err) -> true,
                        (exitCode, err) -> false);

                if (!stopped)
                {
                    logger.info("Cassandra not stopped trying with kill -9");
                    return Response.serverError().entity(Entity.text("Killing Cassandra Failed")).build();
                }

                Uninterruptibles.sleepUninterruptibly(sleepSeconds, TimeUnit.SECONDS);

                maybePid = findPid();
                if (maybePid.isPresent())
                {
                    logger.info("Cassandra is not able to die");
                    return Response.serverError().entity(Entity.text("Killing Cassandra Failed")).build();
                }
            }

            return Response.ok("OK\n").build();
        }
        catch (Throwable t)
        {
            logger.error("Error when calling stop", t);
            return Response.serverError().entity(Entity.text(t.getLocalizedMessage())).build();
        }
    }

    private JsonNode findCassandraYaml() throws IOException
    {
        File cassandraConfig = null;

        if (System.getenv("CASSANDRA_CONF") != null)
        {
            cassandraConfig = new File(System.getenv("CASSANDRA_CONF") + "/cassandra.yaml");
        }
        else
        {
            cassandraConfig = new File(app.dbHome, "conf/cassandra.yaml");
        }

        if (cassandraConfig.exists() && cassandraConfig.isFile())
            return yamlMapper.readTree(cassandraConfig);

        throw new IOException("cassandra.yaml not found");
    }

    @Path("/configure")
    @POST
    @Consumes("application/json")
    @Operation(description = "Configure Cassandra. Will fail if Cassandra is already started")
    public synchronized Response configureNodeJson(@QueryParam("profile") String profile, String config)
    {
        try {
            JsonNode jn = objectMapper.readTree(config);
            String yaml = yamlMapper.writeValueAsString(jn);
            return configureNode(profile, yaml);
        } catch (IOException e) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).entity("Invalid JSON:"+ e.getMessage()).build();
        }
    }

    @Path("/configure")
    @POST
    @Consumes({"application/yaml", "text/yaml"})
    @Operation(description = "Configure Cassandra/DSE. Will fail if Cassandra/DSE is already started")
    public synchronized Response configureNode(@QueryParam("profile") String profile, String yaml)
    {
        if (app.getRequestedState() == STARTED ) {
            return Response.status(Response.Status.NOT_ACCEPTABLE).entity("Cassandra is running, try /api/v0/lifecycle/stop first\n").build();
        }

        //FIXME: Regex
        if (profile == null)
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode()).entity("The profile query parameter is required, try /api/v0/lifecycle/configure?profile=<profile>\n").build();

        //Find cassandra yaml
        try
        {
            JsonNode jn = yamlMapper.readTree(yaml.getBytes());

            jn = jn.get("spec");
            if (jn == null) {
                return Response.status(Response.Status.BAD_REQUEST).entity("spec missing\n").build();
            }

            JsonNode clusterNameJsonNode = jn.get("clusterName");
            if (clusterNameJsonNode == null){
                return Response.status(Response.Status.BAD_REQUEST).entity("cluster name missing\n").build();
            }
            String clusterName = clusterNameJsonNode.textValue();

            jn = jn.get("config");
            if (jn == null)
                return Response.status(Response.Status.BAD_REQUEST.getStatusCode()).entity("config missing\n").build();

            JsonNode nodeTopology = jn.get("node-topology");
            String dc = "vdc1";
            String rack = "vrack1";
            if (nodeTopology != null)
            {
                if (nodeTopology.has("dc"))
                    dc = nodeTopology.get("dc").asText(dc);

                if (nodeTopology.has("rack"))
                    rack = nodeTopology.get("rack").asText(rack);
            }

            new File("/tmp/" + profile).mkdirs();

            StringBuilder sb = new StringBuilder()
                    .append("dc=").append(dc).append("\n")
                    .append("rack=").append(rack).append("\n");

            FileUtils.write(new File("/tmp/" + profile + "/node-topology.properties"), sb.toString(), false);

            JsonNode defaultCYaml = findCassandraYaml();
            JsonNode cassandraYaml = jn.get("cassandra-yaml");

            ObjectNode customYaml = defaultCYaml.deepCopy();

            Iterator<Map.Entry<String, JsonNode>> it = cassandraYaml.fields();
            while (it.hasNext())
            {
                Map.Entry<String, JsonNode> e = it.next();
                customYaml.set(e.getKey(), e.getValue());
            }

            if (clusterName != null)
                customYaml.set("cluster_name", TextNode.valueOf(clusterName));

            yamlMapper.writeValue(new File("/tmp/" + profile + "/cassandra.yaml"), customYaml);

            String jvmMaxHeap = null;
            String jvmHeapNew = null;
            String jvmExtraOpts = null;
            JsonNode jvmOpts = jn.get("jvm-server-options");
            if (jvmOpts != null)
            {
                if (jvmOpts.has("max_heap_size"))
                    jvmMaxHeap = jvmOpts.get("max_heap_size").asText();

                if (jvmOpts.has("heap_new_size"))
                    jvmHeapNew = jvmOpts.get("heap_new_size").asText();

                if ((jvmMaxHeap == null) != (jvmHeapNew == null))
                    return Response.status(Response.Status.BAD_REQUEST).entity("Both max_heap_size and heap_new_size must be defined").build();

                if (jvmOpts.has("additional-jvm-opts"))
                    jvmExtraOpts = Streams.stream(jvmOpts.withArray("additional-jvm-opts").elements())
                            .map(JsonNode::asText).collect(Collectors.joining(" "));
            }

            sb = new StringBuilder();
            sb.append("#!/bin/sh\n");

            if (jvmMaxHeap != null)
                sb.append("export MAX_HEAP_SIZE=").append(jvmMaxHeap).append("\n")
                  .append("export HEAP_NEWSIZE=").append(jvmHeapNew).append("\n");

            if (jvmExtraOpts != null)
                sb.append("export JVM_EXTRA_OPTS=\"$JVM_EXTRA_OPTS ").append(jvmExtraOpts).append("\"\n");

            sb.append("exec $*\n");

            File envScript = new File("/tmp/" + profile + "/env.sh");
            FileUtils.write(envScript, sb.toString(), false);

            envScript.setExecutable(true);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return Response.serverError().build();
        }

        return Response.ok().entity("OK\n").build();
    }

    @Path("/pid")
    @GET
    @Operation(description = "The PID of Cassandra/DSE, if it's running")
    public Response getPID()
    {
        try
        {
            Optional<Integer> maybePid = findPid();
            if (maybePid.isPresent())
                return Response.ok(Integer.toString(maybePid.get())).build();

            return Response.status(HttpStatus.SC_NO_CONTENT).build();
        }
        catch (Throwable t)
        {
            logger.error("Error when getting pid", t);
            return Response.serverError().entity(t.getLocalizedMessage()).build();
        }
    }

    private Optional<Integer> findPid() throws IOException
    {
        return UnixCmds.findDbProcessWithMatchingArg("-Ddb.unix_socket_file=" + app.dbUnixSocketFile.getAbsolutePath());
    }
}
