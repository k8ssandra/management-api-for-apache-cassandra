/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;

import com.datastax.oss.driver.api.core.NoNodeAvailableException;
import com.datastax.oss.driver.api.core.cql.Row;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.swagger.v3.oas.annotations.Operation;

import static org.apache.commons.lang3.StringUtils.EMPTY;

@Path("/api/v0/ops/node")
public class NodeOpsResources
{
    private static final Logger logger = LoggerFactory.getLogger(NodeOpsResources.class);

    private final ManagementApplication app;
    private final CqlService cqlService;

    public static final Map<String, List<String>> classes = ImmutableMap.<String, List<String>>builder()
            .put("bootstrap", ImmutableList.of(
                    "org.apache.cassandra.gms",
                    "org.apache.cassandra.hints",
                    "org.apache.cassandra.schema",
                    "org.apache.cassandra.service.StorageService",
                    "org.apache.cassandra.db.SystemKeyspace",
                    "org.apache.cassandra.batchlog.BatchlogManager",
                    "org.apache.cassandra.net.MessagingService"))
            .put("repair", ImmutableList.of(
                    "org.apache.cassandra.repair",
                    "org.apache.cassandra.db.compaction.CompactionManager",
                    "org.apache.cassandra.service.SnapshotVerbHandler"))
            .put("streaming", ImmutableList.of(
                    "org.apache.cassandra.streaming",
                    "org.apache.cassandra.dht.RangeStreamer"))
            .put("compaction", ImmutableList.of(
                    "org.apache.cassandra.db.compaction",
                    "org.apache.cassandra.db.ColumnFamilyStore",
                    "org.apache.cassandra.io.sstable.IndexSummaryRedistribution"))
            .put("cql", ImmutableList.of(
                    "org.apache.cassandra.cql3",
                    "org.apache.cassandra.auth",
                    "org.apache.cassandra.batchlog",
                    "org.apache.cassandra.net.ResponseVerbHandler",
                    "org.apache.cassandra.service.AbstractReadExecutor",
                    "org.apache.cassandra.service.AbstractWriteResponseHandler",
                    "org.apache.cassandra.service.paxos",
                    "org.apache.cassandra.service.ReadCallback",
                    "org.apache.cassandra.service.ResponseResolver"))
            .put("ring", ImmutableList.of(
                    "org.apache.cassandra.gms",
                    "org.apache.cassandra.service.PendingRangeCalculatorService",
                    "org.apache.cassandra.service.LoadBroadcaster",
                    "org.apache.cassandra.transport.Server"))
            .build();

    public NodeOpsResources(ManagementApplication application) {
        this.app = application;
        this.cqlService = application.cqlService;
    }

    @POST
    @Path("/decommission")
    @Operation(summary = "Decommission the *node I am connecting to*")
    @Produces(MediaType.TEXT_PLAIN)
    public Response decommission(@QueryParam(value="force")boolean force)
    {
        return handle(() ->
        {
            cqlService.executePreparedStatement(app.cassandraUnixSocketFile, "CALL NodeOps.decommission(?)", force);

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/compaction")
    @Operation(summary = "Set the MB/s throughput cap for compaction in the system, or 0 to disable throttling")
    @Produces(MediaType.TEXT_PLAIN)
    public Response setCompactionThroughput(@QueryParam(value="value")int value)
    {
        return handle(() ->
        {
            cqlService.executePreparedStatement(app.cassandraUnixSocketFile, "CALL NodeOps.setCompactionThroughput(?)", value);

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/assassinate")
    @Operation(summary = "Forcefully remove a dead node without re-replicating any data. Use as a last resort if you cannot removenode")
    @Produces(MediaType.TEXT_PLAIN)
    public Response assassinate(@QueryParam(value="address")String address)
    {
        return handle(() ->
        {
            if (StringUtils.isBlank(address)) {
                return Response.status(HttpStatus.SC_BAD_REQUEST).entity("Address must be provided").build();
            }

            cqlService.executePreparedStatement(app.cassandraUnixSocketFile, "CALL NodeOps.assassinate(?)", address);

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/logging")
    @Operation(summary = "Set the log level threshold for a given component or class. Will reset to the initial configuration if called with no parameters.")
    @Produces(MediaType.TEXT_PLAIN)
    public Response setLoggingLevel(@QueryParam(value="target")String targetStr, @QueryParam(value="rawLevel")String rawLevelStr)
    {
        return handle(() ->
        {
            // Retaining logic from org.apache.cassandra.tools.nodetool.SetLoggingLevel
            String target = StringUtils.isNotBlank(targetStr) ? targetStr : EMPTY;
            String rawLevel = StringUtils.isNotBlank(rawLevelStr) ? rawLevelStr : EMPTY;

            List<String> classQualifiers = classes.getOrDefault(target, ImmutableList.of(target));

            for (String classQualifier : classQualifiers)
            {
                cqlService.executePreparedStatement(app.cassandraUnixSocketFile,
                        "CALL NodeOps.setLoggingLevel(?, ?)", classQualifier, rawLevel);
            }

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/drain")
    @Operation(summary = "Drain the node (stop accepting writes and flush all tables)")
    @Produces(MediaType.TEXT_PLAIN)
    public Response drain()
    {
        return handle(() ->
        {
            try
            {
                cqlService.executeCql(app.cassandraUnixSocketFile, "CALL NodeOps.drain()");

                return Response.ok("OK").build();
            }
            catch (com.datastax.oss.driver.api.core.connection.ClosedConnectionException cce)
            {
                // Closed connection is expected when draining the node
                return Response.ok("OK").build();
            }
        });
    }

    @POST
    @Path("/hints/truncate")
    @Operation(summary = "Truncate all hints on the local node, or truncate hints for the endpoint(s) specified.")
    @Produces(MediaType.TEXT_PLAIN)
    public Response truncateHints(@QueryParam(value="host")String host)
    {
        return handle(() ->
        {
            if (StringUtils.isBlank(host))
            {
                cqlService.executeCql(app.cassandraUnixSocketFile, "CALL NodeOps.truncateAllHints()");
            }
            else
            {
                cqlService.executePreparedStatement(app.cassandraUnixSocketFile, "CALL NodeOps.truncateHintsForHost(?)", host);
            }

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/schema/reset")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Reset node's local schema and resync")
    public Response resetLocalSchema()
    {
        return handle(() ->
        {
            cqlService.executeCql(app.cassandraUnixSocketFile, "CALL NodeOps.resetLocalSchema()");

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/schema/reload")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Reload local node schema from system tables")
    public Response reloadLocalSchema()
    {
        return handle(() ->
        {
            cqlService.executeCql(app.cassandraUnixSocketFile, "CALL NodeOps.reloadLocalSchema()");

            return Response.ok("OK").build();
        });
    }

    @GET
    @Path("/streaminfo")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieve Streaming status information")
    public Response getStreamInfo()
    {
        return handle(() ->
        {
            Row row = cqlService.executeCql(app.cassandraUnixSocketFile, "CALL NodeOps.getStreamInfo()").one();

            Object queryResponse = null;
            if (row != null)
            {
                queryResponse = row.getObject(0);
            }
            return Response.ok(Entity.json(queryResponse)).build();
        });
    }

    static Response handle(Callable<Response> action)
    {
        try
        {
            return action.call();
        }
        catch (NoNodeAvailableException | ConnectionClosedException e)
        {
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity("Internal connection to Cassandra closed").build();
        }
        catch (Throwable t)
        {
            logger.error("Error when executing request", t);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity(t.getLocalizedMessage()).build();
        }
    }

}
