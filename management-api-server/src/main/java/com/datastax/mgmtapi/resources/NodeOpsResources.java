/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.RepairRequest;
import com.datastax.mgmtapi.resources.models.SnapshotDetails;
import com.datastax.mgmtapi.resources.models.StreamingInfo;
import com.datastax.mgmtapi.resources.models.TakeSnapshotRequest;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.Map;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/v0/ops/node")
public class NodeOpsResources extends BaseResources {
  private static final Logger logger = LoggerFactory.getLogger(NodeOpsResources.class);

  public static final Map<String, List<String>> classes =
      ImmutableMap.<String, List<String>>builder()
          .put(
              "bootstrap",
              ImmutableList.of(
                  "org.apache.cassandra.gms",
                  "org.apache.cassandra.hints",
                  "org.apache.cassandra.schema",
                  "org.apache.cassandra.service.StorageService",
                  "org.apache.cassandra.db.SystemKeyspace",
                  "org.apache.cassandra.batchlog.BatchlogManager",
                  "org.apache.cassandra.net.MessagingService"))
          .put(
              "repair",
              ImmutableList.of(
                  "org.apache.cassandra.repair",
                  "org.apache.cassandra.db.compaction.CompactionManager",
                  "org.apache.cassandra.service.SnapshotVerbHandler"))
          .put(
              "streaming",
              ImmutableList.of(
                  "org.apache.cassandra.streaming", "org.apache.cassandra.dht.RangeStreamer"))
          .put(
              "compaction",
              ImmutableList.of(
                  "org.apache.cassandra.db.compaction",
                  "org.apache.cassandra.db.ColumnFamilyStore",
                  "org.apache.cassandra.io.sstable.IndexSummaryRedistribution"))
          .put(
              "cql",
              ImmutableList.of(
                  "org.apache.cassandra.cql3",
                  "org.apache.cassandra.auth",
                  "org.apache.cassandra.batchlog",
                  "org.apache.cassandra.net.ResponseVerbHandler",
                  "org.apache.cassandra.service.AbstractReadExecutor",
                  "org.apache.cassandra.service.AbstractWriteResponseHandler",
                  "org.apache.cassandra.service.paxos",
                  "org.apache.cassandra.service.ReadCallback",
                  "org.apache.cassandra.service.ResponseResolver"))
          .put(
              "ring",
              ImmutableList.of(
                  "org.apache.cassandra.gms",
                  "org.apache.cassandra.service.PendingRangeCalculatorService",
                  "org.apache.cassandra.service.LoadBroadcaster",
                  "org.apache.cassandra.transport.Server"))
          .build();

  public NodeOpsResources(ManagementApplication application) {
    super(application);
  }

  @POST
  @Path("/decommission")
  @Operation(summary = "Decommission the *node I am connecting to*", operationId = "decommission")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra node decommissioned successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  public Response decommission(@QueryParam(value = "force") boolean force) {
    return handle(
        () -> {
          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile, "CALL NodeOps.decommission(?, ?)", force, false);

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/compaction")
  @Operation(
      summary =
          "Set the MB/s throughput cap for compaction in the system, or 0 to disable throttling",
      operationId = "setCompactionThroughput")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra compaction throughput set successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  public Response setCompactionThroughput(@QueryParam(value = "value") int value) {
    return handle(
        () -> {
          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile, "CALL NodeOps.setCompactionThroughput(?)", value);

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/assassinate")
  @Operation(
      summary =
          "Forcefully remove a dead node without re-replicating any data. Use as a last resort if you cannot removenode",
      operationId = "assassinate")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra node assasinated successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Cassandra node assasination request missing address",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "Address must be provided")))
  public Response assassinate(@QueryParam(value = "address") String address) {
    return handle(
        () -> {
          if (StringUtils.isBlank(address)) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("Address must be provided")
                .build();
          }

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile, "CALL NodeOps.assassinate(?)", address);

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/logging")
  @Operation(
      summary =
          "Set the log level threshold for a given component or class. Will reset to the initial configuration if called with no parameters.",
      operationId = "setLoggingLevel")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra logging level set successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  public Response setLoggingLevel(
      @QueryParam(value = "target") String targetStr,
      @QueryParam(value = "rawLevel") String rawLevelStr) {
    return handle(
        () -> {
          // Retaining logic from org.apache.cassandra.tools.nodetool.SetLoggingLevel
          String target = StringUtils.isNotBlank(targetStr) ? targetStr : EMPTY;
          String rawLevel = StringUtils.isNotBlank(rawLevelStr) ? rawLevelStr : EMPTY;

          List<String> classQualifiers = classes.getOrDefault(target, ImmutableList.of(target));

          for (String classQualifier : classQualifiers) {
            app.cqlService.executePreparedStatement(
                app.dbUnixSocketFile,
                "CALL NodeOps.setLoggingLevel(?, ?)",
                classQualifier,
                rawLevel);
          }

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/drain")
  @Operation(
      summary = "Drain the node (stop accepting writes and flush all tables)",
      operationId = "drain")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra node drained successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  // TODO Make async
  public Response drain() {
    return handle(
        () -> {
          try {
            app.cqlService.executeSlowCql(app.dbUnixSocketFile, "CALL NodeOps.drain()");

            return Response.ok("OK").build();
          } catch (com.datastax.oss.driver.api.core.connection.ClosedConnectionException cce) {
            // Closed connection is expected when draining the node
            return Response.ok("OK").build();
          }
        });
  }

  @POST
  @Path("/hints/truncate")
  @Operation(
      summary =
          "Truncate all hints on the local node, or truncate hints for the endpoint(s) specified.",
      operationId = "truncateHints")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra node hints truncated successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  public Response truncateHints(@QueryParam(value = "host") String host) {
    return handle(
        () -> {
          if (StringUtils.isBlank(host)) {
            app.cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.truncateAllHints()");
          } else {
            app.cqlService.executePreparedStatement(
                app.dbUnixSocketFile, "CALL NodeOps.truncateHintsForHost(?)", host);
          }

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/schema/reset")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra node scheam resynced successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @Operation(summary = "Reset node's local schema and resync", operationId = "resetLocalSchema")
  public Response resetLocalSchema() {
    return handle(
        () -> {
          app.cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.resetLocalSchema()");

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/schema/reload")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra node schema reloaded successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @Operation(
      summary = "Reload local node schema from system tables",
      operationId = "reloadLocalSchema")
  public Response reloadLocalSchema() {
    return handle(
        () -> {
          app.cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.reloadLocalSchema()");

          return Response.ok("OK").build();
        });
  }

  @GET
  @Path("/streaminfo")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra streaming status info",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StreamingInfo.class)))
  @Operation(summary = "Retrieve Streaming status information", operationId = "getStreamInfo")
  public Response getStreamInfo() {
    return handle(
        () -> {
          Row row =
              app.cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.getStreamInfo()").one();

          Object queryResponse = null;
          if (row != null) {
            queryResponse = row.getObject(0);
          }
          return Response.ok(Entity.json(queryResponse)).build();
        });
  }

  @GET
  @Path("/snapshots")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(
      responseCode = "200",
      description =
          "Cassandra snapshot details. Use 'null' values for query parameters to exclude result filtering against the parameter.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = SnapshotDetails.class)))
  @Operation(summary = "Retrieve snapshot details", operationId = "getSnapshotDetails")
  public Response getSnapshotDetails(
      @QueryParam("snapshotNames") List<String> snapshotNames,
      @QueryParam("keyspaces") List<String> keyspace) {
    return handle(
        () -> {
          Row row =
              app.cqlService
                  .executePreparedStatement(
                      app.dbUnixSocketFile,
                      "CALL NodeOps.getSnapshotDetails(?, ?)",
                      snapshotNames,
                      keyspace)
                  .one();

          Object queryResponse = null;
          if (row != null) {
            queryResponse = row.getObject(0);
          }
          return Response.ok(Entity.json(queryResponse)).build();
        });
  }

  @POST
  @Path("/snapshots")
  @Consumes("application/json")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Snapshot created successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid snapshot request",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples =
                  @ExampleObject(
                      value =
                          "When specifying keyspace_tables, specifying keyspaces is not allowed")))
  @Operation(summary = "Take a snapshot", operationId = "takeSnapshot")
  public Response takeSnapshot(TakeSnapshotRequest takeSnapshotRequest) {
    return handle(
        () -> {
          String snapshotName = takeSnapshotRequest.snapshotName;
          List<String> keyspaces = takeSnapshotRequest.keyspaces;
          String tableName = takeSnapshotRequest.tableName;
          Boolean skipFlsuh = takeSnapshotRequest.skipFlush;
          List<String> keyspaceTables = takeSnapshotRequest.keyspaceTables;

          if (keyspaces != null && !keyspaces.isEmpty()) {
            if (keyspaceTables != null && !keyspaceTables.isEmpty()) {
              // when specifying Keyspace.table lists, you can not specify any keyspaces
              return Response.status(Response.Status.BAD_REQUEST)
                  .entity("When specifying keyspace_tables, specifying keyspaces is not allowed")
                  .build();
            }
            if (tableName != null && keyspaces.size() > 1) {
              // when specifying a table name (column family), you must specify exactly 1 keyspace
              return Response.status(Response.Status.BAD_REQUEST)
                  .entity("Exactly 1 keyspace must be specified when specifying table_name")
                  .build();
            }
          } else {
            // no keyspaces specified
            if (tableName != null) {
              // when specifying a table name (column family), you must specify exactly 1 keyspace
              return Response.status(Response.Status.BAD_REQUEST)
                  .entity("Exactly 1 keyspace must be specified when specifying table_name")
                  .build();
            }
          }

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.takeSnapshot(?, ?, ?, ?, ?)",
              snapshotName,
              keyspaces,
              tableName,
              skipFlsuh,
              keyspaceTables);

          return Response.ok("OK").build();
        });
  }

  @DELETE
  @Path("/snapshots")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Snapshots cleared successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @Operation(summary = "Clear snapshots", operationId = "clearSnapshots")
  public Response clearSnapshots(
      @QueryParam(value = "snapshotNames") List<String> snapshotNames,
      @QueryParam(value = "keyspaces") List<String> keyspaces) {
    return handle(
        () -> {
          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile, "CALL NodeOps.clearSnapshots(?, ?)", snapshotNames, keyspaces);
          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/repair")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Nodetool repair executed successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Repair request missing Keyspace name",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "keyspaceName must be specified")))
  @Operation(summary = "Execute a nodetool repair operation", operationId = "repair")
  public Response repair(RepairRequest repairRequest) {
    return handle(
        () -> {
          if (repairRequest.keyspaceName == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("keyspaceName must be specified")
                .build();
          }
          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.repair(?, ?, ?, ?, ?, ?, ?, ?)",
              repairRequest.keyspaceName,
              repairRequest.tables,
              repairRequest.full,
              false,
              // The default repair does not allow for specifying things like parallelism,
              // threadCounts, source DCs or ranges etc.
              null,
              null,
              null,
              null);

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/fullquerylogging")
  @Operation(
      summary = "Enable or disable full query logging facility.",
      operationId = "setFullQuerylog")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Full Query Logging set successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  public Response setFullQuerylog(@QueryParam(value = "enabled") boolean fullQueryLoggingEnabled) {
    return handle(
        () -> {
          logger.debug("Running CALL NodeOps.setFullQuerylog(?) " + fullQueryLoggingEnabled);
          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile, "CALL NodeOps.setFullQuerylog(?)", fullQueryLoggingEnabled);
          return Response.ok("OK").build();
        });
  }

  @GET
  @Path("/fullquerylogging")
  @Operation(
      summary = "Get whether full query logging is enabled.",
      operationId = "isFullQueryLogEnabled")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(
      responseCode = "200",
      description = "Full Query enabled",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = FQL_QUERY_RESPONSE_EXAMPLE)))
  public Response isFullQueryLogEnabled() {
    return handle(
        () -> {
          logger.debug("CALL NodeOps.isFullQueryLogEnabled()");
          Row row =
              app.cqlService
                  .executePreparedStatement(
                      app.dbUnixSocketFile, "CALL NodeOps.isFullQueryLogEnabled()")
                  .one();
          Object queryResponse = null;
          if (row != null) {
            queryResponse = row.getObject(0);
          }
          return Response.ok(Entity.json(queryResponse)).build();
        });
  }

  @POST
  @Path("/move")
  @Produces(MediaType.TEXT_PLAIN)
  @Operation(
      summary =
          "Move node on the token ring to a new token. This operation returns immediately with a job id.",
      operationId = "move")
  @ApiResponse(
      responseCode = "202",
      description = "Job ID for successfully scheduled Cassandra node move request",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "d69d1d95-9348-4460-95d2-ae342870fade")))
  @ApiResponse(
      responseCode = "400",
      description = "Missing required newToken parameter",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "newToken must be specified")))
  public Response move(@QueryParam(value = "newToken") String newToken) {
    return handle(
        () -> {
          if (StringUtils.isBlank(newToken)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("newToken must be specified")
                .build();
          }

          return Response.accepted(
                  ResponseTools.getSingleRowStringResponse(
                      app.dbUnixSocketFile,
                      app.cqlService,
                      "CALL NodeOps.move(?, ?)",
                      newToken,
                      true))
              .build();
        });
  }

  @POST
  @Path("/search/rebuildIndex")
  @Operation(summary = "Rebuild a DSE Search index", operationId = "searchIndexRebuild")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(responseCode = "200", description = "DSE Search index rebuild has started")
  @ApiResponse(
      responseCode = "500",
      description =
          "Internal error occurs that disallow us to determine if this operation is possible")
  @ApiResponse(
      responseCode = "400",
      description =
          "An attempt is made to rebuild the index on a server type (Cassandra) that does not support it")
  @ApiResponse(
      responseCode = "404",
      description = "An attempt is made to rebuild a non-existing index")
  public Response searchIndexRebuild(
      @QueryParam(value = "keyspace") String keyspace, @QueryParam(value = "table") String table) {
    return handle(
        () -> {
          // check if we're dealing with DSE
          final String releaseVersion =
              ResponseTools.getSingleRowStringResponse(
                  app.dbUnixSocketFile, app.cqlService, CASSANDRA_VERSION_CQL_STRING);
          if (releaseVersion == null) {
            // couldn't get release version, something is wrong
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
          }
          if (!releaseVersion.startsWith("4.0.0.68") && !releaseVersion.startsWith("4.0.0.69")) {
            // rebuilding search index is only possible on DSE
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Rebuilding Search Index is only supported on DSE")
                .build();
          }
          try {
            String rebuild_query = String.format("REBUILD SEARCH INDEX ON %s.%s;", keyspace, table);
            app.cqlService.executeCql(app.dbUnixSocketFile, rebuild_query);
          } catch (InvalidQueryException iqe) {
            return Response.status(Response.Status.NOT_FOUND).build();
          }

          return Response.status(Response.Status.OK).build();
        });
  }

  @POST
  @Path("/encryption/internode/truststore/reload")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Truststore reloaded successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Unsupported Operation",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples =
                  @ExampleObject(
                      value = "Reloading the truststore manually is only possible on DSE")))
  @Operation(summary = "reload truststore", operationId = "reloadTruststore")
  public Response reloadTruststore() {
    return handle(
        () -> {
          final String releaseVersion =
              ResponseTools.getSingleRowStringResponse(
                  app.dbUnixSocketFile, app.cqlService, CASSANDRA_VERSION_CQL_STRING);
          if (!releaseVersion.startsWith("4.0.0.68") && !releaseVersion.startsWith("4.0.0.69")) {
            // rebuilding search index is only possible on DSE
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Reloading the truststore manually is only possible on DSE")
                .build();
          }

          app.cqlService.executeCql(
              app.dbUnixSocketFile, "CALL NodeOps.reloadInternodeEncryptionTruststore()");

          return Response.ok("OK").build();
        });
  }

  private static final String FQL_QUERY_RESPONSE_EXAMPLE =
      "{\n"
          + "    \"entity\": false,\n"
          + "    \"variant\": {\n"
          + "        \"language\": null,\n"
          + "        \"mediaType\": {\n"
          + "            \"type\": \"application\",\n"
          + "            \"subtype\": \"json\",\n"
          + "            \"parameters\": {},\n"
          + "            \"wildcardType\": false,\n"
          + "            \"wildcardSubtype\": false\n"
          + "        },\n"
          + "        \"encoding\": null,\n"
          + "        \"languageString\": null\n"
          + "    },\n"
          + "    \"annotations\": [],\n"
          + "    \"mediaType\": {\n"
          + "        \"type\": \"application\",\n"
          + "        \"subtype\": \"json\",\n"
          + "        \"parameters\": {},\n"
          + "        \"wildcardType\": false,\n"
          + "        \"wildcardSubtype\": false\n"
          + "    },\n"
          + "    \"language\": null,\n"
          + "    \"encoding\": null\n"
          + "}";
}
