/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.bdp.management.resources;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.bdp.management.ManagementApplication;
import com.datastax.bdp.management.UnixCmds;
import com.datastax.bdp.management.UnixSocketCQLAccess;
import com.datastax.bdp.util.ShellUtils;
import com.datastax.oss.driver.api.core.CqlSession;
import io.swagger.v3.oas.annotations.Operation;
import org.apache.http.HttpStatus;

import static com.datastax.bdp.management.ManagementApplication.STATE.STARTED;
import static com.datastax.bdp.management.ManagementApplication.STATE.STOPPED;

@Path("/api/v0/lifecycle")
public class LifecycleResources
{
    private static final Logger logger = LoggerFactory.getLogger(LifecycleResources.class);
    private final ManagementApplication app;

    public LifecycleResources(ManagementApplication app)
    {
        this.app = app;
    }

    /**
     * Starts a DSE node
     *
     * Handles the following:
     *   - DSE pid found and can connect: status 202
     *   - DSE pid not found and can not connect: status 201
     *   - DSE pid not found but can connect: status 206
     *   - DSE pid found but can not connect: status 204
     *
     *
     * @return
     */
    @Path("/start")
    @POST
    @Operation(description = "Starts DSE")
    public synchronized Response startNode()
    {
        app.setRequestedState(STARTED);

        //Todo we should add a CALL getPid command and compare;
        boolean canConnect;
        try
        {
            CqlSession session = UnixSocketCQLAccess.get(app.dseUnixSocketFile).get();
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
            Map<String,String> environment = ImmutableMap.of("JVM_EXTRA_OPTS","-javaagent:/home/jake/workspace/oss-management-api/management-api-agent/target/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar");

            boolean started = ShellUtils.executeShellWithHandlers(
                    String.format("nohup %s -R -Dcassandra.server_process -Dcassandra.skip_default_role_setup=true -Dcassandra.unix_socket_file=%s %s 1>&2",
                            app.dseCmdFile.getAbsolutePath(), app.dseUnixSocketFile.getAbsolutePath(),
                            String.join(" ", app.dseExtraArgs)),
                    (input, err) -> true,
                    (exitCode, err) -> {
                        logger.error("Error starting DSE: {}", err.lines().collect(Collectors.joining("\n")));
                        return false;
                    },
                    environment);

            if (started)
                logger.info("Started DSE");
            else
                logger.warn("Error starting dse");

            return Response.status(started ? HttpStatus.SC_CREATED : HttpStatus.SC_METHOD_FAILURE).build();
        }
        catch (Throwable t)
        {
            return Response.serverError().entity(Entity.text(t.getLocalizedMessage())).build();
        }
    }

    @Path("/stop")
    @POST
    @Operation(description = "Stops DSE. Keeps node from restarting automatically until /start is called")
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
                    logger.info("DSE already stopped");
                    return Response.ok("OK").build();
                }

                Boolean stopped = ShellUtils.executeShellWithHandlers(
                        String.format("kill %d", maybePid.get()),
                        (input, err) -> true,
                        (exitCode, err) -> false);

                if (!stopped)
                    logger.warn("Killing DSE failed");

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
                    logger.info("DSE not stopped trying with kill -9");
                    return Response.serverError().entity(Entity.text("Killing DSE Failed")).build();
                }

                Uninterruptibles.sleepUninterruptibly(sleepSeconds, TimeUnit.SECONDS);

                maybePid = findPid();
                if (maybePid.isPresent())
                {
                    logger.info("DSE is not able to die");
                    return Response.serverError().entity(Entity.text("Killing DSE Failed")).build();
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

    @Path("/configure")
    @POST
    @Operation(description = "Configure C*. Will fail if C* is already started")
    public synchronized Response configureNode()
    {
        app.setRequestedState(STOPPED);

        return Response.ok().build();
    }

    @Path("/pid")
    @GET
    @Operation(description = "The PID of DSE if it's running")
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
        return UnixCmds.findDseWithMatchingArg("-Dcassandra.unix_socket_file=" + app.dseUnixSocketFile.getAbsolutePath());
    }
}
