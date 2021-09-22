/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import javax.ws.rs.DELETE;
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
import com.datastax.mgmtapi.resources.models.RepairRequest;
import com.datastax.mgmtapi.resources.models.TakeSnapshotRequest;

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
            cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.decommission(?)", force);

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
            cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.setCompactionThroughput(?)", value);

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

            cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.assassinate(?)", address);

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
                cqlService.executePreparedStatement(app.dbUnixSocketFile,
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
                cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.drain()");

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
                cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.truncateAllHints()");
            }
            else
            {
                cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.truncateHintsForHost(?)", host);
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
            cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.resetLocalSchema()");

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
            cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.reloadLocalSchema()");

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
            Row row = cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.getStreamInfo()").one();

            Object queryResponse = null;
            if (row != null)
            {
                queryResponse = row.getObject(0);
            }
            return Response.ok(Entity.json(queryResponse)).build();
        });
    }

    @GET
    @Path("/snapshots")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieve snapshot details")
    public Response getSnapshotDetails(@QueryParam("snapshotNames") List<String> snapshotNames, @QueryParam("keyspaces") List<String> keyspace)
    {
        return handle(() ->
        {
            Row row = cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.getSnapshotDetails(?, ?)", snapshotNames, keyspace).one();

            Object queryResponse = null;
            if (row != null)
            {
                queryResponse = row.getObject(0);
            }
            return Response.ok(Entity.json(queryResponse)).build();
        });
    }

    @POST
    @Path("/snapshots")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Take a snapshot")
    public Response takeSnapshot(TakeSnapshotRequest takeSnapshotRequest)
    {
        return handle(() ->
        {
            String snapshotName = takeSnapshotRequest.snapshotName;
            List<String> keyspaces = takeSnapshotRequest.keyspaces;
            String tableName = takeSnapshotRequest.tableName;
            Boolean skipFlsuh = takeSnapshotRequest.skipFlush;
            List<String> keyspaceTables = takeSnapshotRequest.keyspaceTables;

            if (keyspaces != null && !keyspaces.isEmpty())
            {
                if (keyspaceTables != null && !keyspaceTables.isEmpty())
                {
                    // when specifying Keyspace.table lists, you can not specify any keyspaces
                    return Response.status(Response.Status.BAD_REQUEST).entity("When specifying keyspace_tables, specifying keyspaces is not allowed").build();
                }
                if (tableName != null && keyspaces.size() > 1)
                {
                    // when specifying a table name (column family), you must specify exactly 1 keyspace
                    return Response.status(Response.Status.BAD_REQUEST).entity("Exactly 1 keyspace must be specified when specifying table_name").build();
                }
            }
            else
            {
                // no keyspaces specified
                if (tableName != null)
                {
                    // when specifying a table name (column family), you must specify exactly 1 keyspace
                    return Response.status(Response.Status.BAD_REQUEST).entity("Exactly 1 keyspace must be specified when specifying table_name").build();
                }
            }

            cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.takeSnapshot(?, ?, ?, ?, ?)", snapshotName, keyspaces, tableName, skipFlsuh, keyspaceTables);

            return Response.ok("OK").build();
        });
    }

    @DELETE
    @Path("/snapshots")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Clear snapshots")
    public Response clearSnapshots(@QueryParam(value="snapshotNames") List<String> snapshotNames, @QueryParam(value="keyspaces") List<String> keyspaces)
    {
        return handle(() ->
        {
            cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.clearSnapshots(?, ?)", snapshotNames, keyspaces);
            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/repair")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Perform a nodetool repair")
    public Response repair(RepairRequest repairRequest)
    {
        return handle(() ->
        {
            if (repairRequest.keyspaceName == null)
            {
                return Response.status(Response.Status.BAD_REQUEST).entity("keyspaceName must be specified").build();
            }
            cqlService.executePreparedStatement(
                    app.dbUnixSocketFile,
                    "CALL NodeOps.repair(?, ?, ?)",
                    repairRequest.keyspaceName,
                    repairRequest.tables,
                    repairRequest.full);

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/fullquerylogging")
    @Operation(summary = "Enable or disable full query logging facility.")
    @Produces(MediaType.TEXT_PLAIN)
    public Response setFullQuerylog(@QueryParam(value="enabled")boolean fullQueryLoggingEnabled)
    {
        return handle(() ->
        {   logger.debug("Running CALL NodeOps.setFullQuerylog(?) " + fullQueryLoggingEnabled);
            cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.setFullQuerylog(?)", fullQueryLoggingEnabled);
            return Response.ok("OK").build();
        });
    }

    @GET
    @Path("/fullquerylogging")
    @Operation(summary = "Get whether full query logging is enabled.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response isFullQueryLogEnabled()
    {
        return handle(() ->
        {
            logger.debug("CALL NodeOps.isFullQueryLogEnabled()");
            Row row = cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.isFullQueryLogEnabled()").one();
            Object queryResponse = null;
            if (row != null) { queryResponse = row.getObject(0); }
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
