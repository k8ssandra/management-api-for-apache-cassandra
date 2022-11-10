/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import javax.ws.rs.Consumes;
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

import com.datastax.mgmtapi.resources.helpers.ResponseTools;
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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

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
    @Operation(summary = "Decommission the *node I am connecting to*", operationId = "decommission")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Cassandra node decommissioned successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
    public Response decommission(@QueryParam(value="force")boolean force)
    {
        return handle(() ->
        {
            cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.decommission(?, ?)", force, false);

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/compaction")
    @Operation(summary = "Set the MB/s throughput cap for compaction in the system, or 0 to disable throttling", operationId = "setCompactionThroughput")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Cassandra compaction throughput set successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
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
    @Operation(summary = "Forcefully remove a dead node without re-replicating any data. Use as a last resort if you cannot removenode", operationId = "assassinate")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Cassandra node assasinated successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
    @ApiResponse(responseCode = "400", description = "Cassandra node assasination request missing address",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "Address must be provided")
        )
    )
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
    @Operation(summary = "Set the log level threshold for a given component or class. Will reset to the initial configuration if called with no parameters.", operationId = "setLoggingLevel")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Cassandra logging level set successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
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
    @Operation(summary = "Drain the node (stop accepting writes and flush all tables)", operationId = "drain")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Cassandra node drained successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
    // TODO Make async
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
    @Operation(summary = "Truncate all hints on the local node, or truncate hints for the endpoint(s) specified.", operationId = "truncateHints")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Cassandra node hints truncated successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
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
    @ApiResponse(responseCode = "200", description = "Cassandra node scheam resynced successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
    @Operation(summary = "Reset node's local schema and resync", operationId = "resetLocalSchema")
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
    @ApiResponse(responseCode = "200", description = "Cassandra node schema reloaded successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
    @Operation(summary = "Reload local node schema from system tables", operationId = "reloadLocalSchema")
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
    @ApiResponse(responseCode = "200", description = "Cassandra streaming status info",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = STREAMING_INFO_RESPONSE_EXAMPLE)
        )
    )
    @Operation(summary = "Retrieve Streaming status information", operationId = "getStreamInfo")
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
    @ApiResponse(responseCode = "200", description = "Cassandra snapshot details",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = SNAPSHOT_DETAILS_RESPONSE_EXAMPLE)
        )
    )
    @Operation(summary = "Retrieve snapshot details", operationId = "getSnapshotDetails")
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
    @Consumes("application/json")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Snapshot created successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
    @ApiResponse(responseCode = "400", description = "Invalid snapshot request",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "When specifying keyspace_tables, specifying keyspaces is not allowed")
        )
    )
    @Operation(summary = "Take a snapshot", operationId = "takeSnapshot")
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
    @ApiResponse(responseCode = "200", description = "Snapshots cleared successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
    @Operation(summary = "Clear snapshots", operationId = "clearSnapshots")
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
    @ApiResponse(responseCode = "200", description = "Nodetool repair executed successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
    @ApiResponse(responseCode = "400", description = "Repair request missing Keyspace name",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "keyspaceName must be specified")
        )
    )
    @Operation(summary = "Execute a nodetool repair operation", operationId = "repair")
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
    @Operation(summary = "Enable or disable full query logging facility.", operationId = "setFullQuerylog")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Full Query Logging set successfully",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "OK")
        )
    )
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
    @Operation(summary = "Get whether full query logging is enabled.", operationId = "isFullQueryLogEnabled")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponse(responseCode = "200", description = "Full Query enabled",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = FQL_QUERY_RESPONSE_EXAMPLE)
        )
    )
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

    @POST
    @Path("/move")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Move node on the token ring to a new token. This operation returns immediately with a job id.", operationId = "move")
    @ApiResponse(responseCode = "202", description = "Job ID for successfully scheduled Cassandra node move request",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(
                implementation = String.class
            ),
            examples = @ExampleObject(
                value = "d69d1d95-9348-4460-95d2-ae342870fade"
            )
        )
    )
    @ApiResponse(responseCode = "400", description = "Missing required newToken parameter",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "newToken must be specified")
        )
    )
    public Response move(@QueryParam(value="newToken")String newToken)
    {
        return handle(() ->
                      {
                          if (StringUtils.isBlank(newToken))
                          {
                              return Response.status(Response.Status.BAD_REQUEST).entity("newToken must be specified").build();
                          }

                          return Response.accepted(ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService,
                                                                              "CALL NodeOps.move(?, ?)", newToken, true)).build();
        });
    }

    public static Response handle(Callable<Response> action)
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

    private static final String STREAMING_INFO_RESPONSE_EXAMPLE = "{\n" +
"    \"entity\": [],\n" +
"    \"variant\": {\n" +
"        \"language\": null,\n" +
"        \"mediaType\": {\n" +
"            \"type\": \"application\",\n" +
"            \"subtype\": \"json\",\n" +
"            \"parameters\": {},\n" +
"            \"wildcardType\": false,\n" +
"            \"wildcardSubtype\": false\n" +
"        },\n" +
"        \"encoding\": null,\n" +
"        \"languageString\": null\n" +
"    },\n" +
"    \"annotations\": [],\n" +
"    \"mediaType\": {\n" +
"        \"type\": \"application\",\n" +
"        \"subtype\": \"json\",\n" +
"        \"parameters\": {},\n" +
"        \"wildcardType\": false,\n" +
"        \"wildcardSubtype\": false\n" +
"    },\n" +
"    \"language\": null,\n" +
"    \"encoding\": null\n" +
"}";

    private static final String SNAPSHOT_DETAILS_RESPONSE_EXAMPLE = "{\n" +
"    \"entity\": [\n" +
"        {\n" +
"            \"Column family name\": \"size_estimates\",\n" +
"            \"Keyspace name\": \"system\",\n" +
"            \"Size on disk\": \"13 bytes\",\n" +
"            \"Snapshot name\": \"truncated-1639687082845-size_estimates\",\n" +
"            \"True size\": \"0 bytes\"\n" +
"        },\n" +
"        {\n" +
"            \"Column family name\": \"table_estimates\",\n" +
"            \"Keyspace name\": \"system\",\n" +
"            \"Size on disk\": \"13 bytes\",\n" +
"            \"Snapshot name\": \"truncated-1639687082982-table_estimates\",\n" +
"            \"True size\": \"0 bytes\"\n" +
"        }\n" +
"    ],\n" +
"    \"variant\": {\n" +
"        \"language\": null,\n" +
"        \"mediaType\": {\n" +
"            \"type\": \"application\",\n" +
"            \"subtype\": \"json\",\n" +
"            \"parameters\": {},\n" +
"            \"wildcardType\": false,\n" +
"            \"wildcardSubtype\": false\n" +
"        },\n" +
"        \"encoding\": null,\n" +
"        \"languageString\": null\n" +
"    },\n" +
"    \"annotations\": [],\n" +
"    \"mediaType\": {\n" +
"        \"type\": \"application\",\n" +
"        \"subtype\": \"json\",\n" +
"        \"parameters\": {},\n" +
"        \"wildcardType\": false,\n" +
"        \"wildcardSubtype\": false\n" +
"    },\n" +
"    \"language\": null,\n" +
"    \"encoding\": null\n" +
"}";

    private static final String FQL_QUERY_RESPONSE_EXAMPLE = "{\n" +
"    \"entity\": false,\n" +
"    \"variant\": {\n" +
"        \"language\": null,\n" +
"        \"mediaType\": {\n" +
"            \"type\": \"application\",\n" +
"            \"subtype\": \"json\",\n" +
"            \"parameters\": {},\n" +
"            \"wildcardType\": false,\n" +
"            \"wildcardSubtype\": false\n" +
"        },\n" +
"        \"encoding\": null,\n" +
"        \"languageString\": null\n" +
"    },\n" +
"    \"annotations\": [],\n" +
"    \"mediaType\": {\n" +
"        \"type\": \"application\",\n" +
"        \"subtype\": \"json\",\n" +
"        \"parameters\": {},\n" +
"        \"wildcardType\": false,\n" +
"        \"wildcardSubtype\": false\n" +
"    },\n" +
"    \"language\": null,\n" +
"    \"encoding\": null\n" +
"}";
}
