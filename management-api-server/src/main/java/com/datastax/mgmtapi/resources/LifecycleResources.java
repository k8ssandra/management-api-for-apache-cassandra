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
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
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
import com.fasterxml.jackson.databind.JsonNode;
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

    public LifecycleResources(ManagementApplication app)
    {
        this.app = app;
    }

    /**
     * Starts a Cassandra node
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
    @Operation(description = "Starts Cassandra")
    public synchronized Response startNode(@QueryParam("profile") String profile)
    {
        app.setRequestedState(STARTED);

        //Todo we should add a CALL getPid command and compare;
        boolean canConnect;
        try
        {
            CqlSession session = UnixSocketCQLAccess.get(app.cassandraUnixSocketFile).get();
            session.execute("SELECT * FROM system.peers");

            canConnect = true;
        }
        catch (Throwable t)
        {
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
            StringBuilder profileArgs = new StringBuilder();

            if (profile != null)
            {
                app.setActiveProfile(profile);

                profileArgs.append("-Dcassandra.config=file:///tmp/").append(profile).append("/cassandra.yaml")
                        .append(" -Dcassandra-rackdc.properties=file:///tmp/").append(profile).append("/node-topology.properties");
            }

            boolean started = ShellUtils.executeShellWithHandlers(
                    String.format("nohup %s -R -Dcassandra.server_process -Dcassandra.skip_default_role_setup=true -Dcassandra.unix_socket_file=%s %s %s 1>&2",
                            app.cassandraExe.getAbsolutePath(),
                            app.cassandraUnixSocketFile.getAbsolutePath(),
                            profileArgs.toString(),
                            String.join(" ", app.cassandraExtraArgs)),
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

            return Response.status(started ? HttpStatus.SC_CREATED : HttpStatus.SC_METHOD_FAILURE).build();
        }
        catch (Throwable t)
        {
            return Response.serverError().entity(Entity.text(t.getLocalizedMessage())).build();
        }
    }

    @Path("/stop")
    @POST
    @Operation(description = "Stops Cassandra. Keeps node from restarting automatically until /start is called")
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
                    return Response.ok("OK").build();
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

            return Response.ok("OK").build();
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
            cassandraConfig = new File(app.cassandraHome, "conf/cassandra.yaml");
        }

        if (cassandraConfig.exists() && cassandraConfig.isFile())
            return yamlMapper.readTree(cassandraConfig);

        throw new IOException("cassandra.yaml not found");
    }

    @Path("/configure")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes("application/yaml")
    @Operation(description = "Configure Cassandra. Will fail if Cassandra is already started")
    public synchronized Response configureNode(@QueryParam("profile") String profile, String yaml)
    {
        if (app.getRequestedState() == STARTED)
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();

        //FIXME: Regex
        if (profile == null)
            return Response.status(Response.Status.BAD_REQUEST).build();

        //Find cassandra yaml
        try
        {
            JsonNode jn = yamlMapper.readTree(yaml.getBytes());

            jn = jn.get("spec");
            if (jn == null)
                return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "spec missing").build();

            String clusterName = jn.get("clusterName").textValue();
            jn = jn.get("config");
            if (jn == null)
                return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "config missing").build();

            JsonNode nodeTopology = jn.get("node-topology");
            String dc = "vdc1";
            String rack = "vrack1";
            if (nodeTopology != null)
            {
                dc = nodeTopology.get("dc").asText("vdc1");
                rack = nodeTopology.get("rack").asText("vrack1");
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
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return Response.serverError().build();
        }

        return Response.ok().build();
    }

    @Path("/pid")
    @GET
    @Operation(description = "The PID of Cassandra, if it's running")
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
        return UnixCmds.findCassandraWithMatchingArg("-Dcassandra.unix_socket_file=" + app.cassandraUnixSocketFile.getAbsolutePath());
    }
}
