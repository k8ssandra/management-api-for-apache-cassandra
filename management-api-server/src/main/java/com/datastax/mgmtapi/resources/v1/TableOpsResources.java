/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v1;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.CompactRequest;
import com.datastax.mgmtapi.resources.models.Compaction;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import com.datastax.mgmtapi.resources.models.ScrubRequest;
import com.datastax.mgmtapi.resources.models.Table;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

@Path("/api/v1/ops/tables")
public class TableOpsResources extends BaseResources {

  private static final GenericType<List<Map<String, String>>> LIST_OF_MAP_OF_STRINGS =
      GenericType.listOf(GenericType.mapOf(String.class, String.class));

  public TableOpsResources(ManagementApplication application) {
    super(application);
  }

  @POST
  @Path("/scrub")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "202",
      description = "Job ID for table scrub process",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "d69d1d95-9348-4460-95d2-ae342870fade")))
  @Operation(
      summary =
          "Scrub (rebuild sstables for) one or more tables. This operation is asynchronous and returns immediately.",
      operationId = "scrub")
  public Response scrub(ScrubRequest scrubRequest) {
    return handle(
        () -> {
          List<String> tables = scrubRequest.tables;
          if (CollectionUtils.isEmpty(tables)) {
            tables = new ArrayList<>();
          }

          String keyspaceName = scrubRequest.keyspaceName;
          if (StringUtils.isBlank(keyspaceName)) {
            keyspaceName = "ALL";
          }

          return Response.accepted(
                  ResponseTools.getSingleRowStringResponse(
                      app.dbUnixSocketFile,
                      app.cqlService,
                      "CALL NodeOps.scrub(?, ?, ?, ?, ?, ?, ?, ?)",
                      scrubRequest.disableSnapshot,
                      scrubRequest.skipCorrupted,
                      scrubRequest.checkData,
                      scrubRequest.reinsertOverflowedTTL,
                      scrubRequest.jobs,
                      keyspaceName,
                      tables,
                      true))
              .build();
        });
  }

  @POST
  @Path("/sstables/upgrade")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "202",
      description = "Job ID for keyspace SSTable upgrade process",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "d69d1d95-9348-4460-95d2-ae342870fade")))
  @Operation(
      summary =
          "Rewrite sstables (for the requested tables) that are not on the current version (thus upgrading them to said current version). This operation is asynchronous and returns immediately.",
      operationId = "upgradeSSTables")
  public Response upgradeSSTables(
      @QueryParam(value = "excludeCurrentVersion") boolean excludeCurrentVersion,
      KeyspaceRequest keyspaceRequest) {
    return handle(
        () -> {
          List<String> tables = keyspaceRequest.tables;
          if (CollectionUtils.isEmpty(tables)) {
            tables = new ArrayList<>();
          }

          String keyspaceName = keyspaceRequest.keyspaceName;
          if (StringUtils.isBlank(keyspaceName)) {
            keyspaceName = "ALL";
          }

          return Response.accepted(
                  ResponseTools.getSingleRowStringResponse(
                      app.dbUnixSocketFile,
                      app.cqlService,
                      "CALL NodeOps.upgradeSSTables(?, ?, ?, ?, ?)",
                      keyspaceName,
                      !excludeCurrentVersion,
                      keyspaceRequest.jobs,
                      tables,
                      true))
              .build();
        });
  }

  @POST
  @Path("/compact")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "202",
      description = "Job ID for keyspace compaction process",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "d69d1d95-9348-4460-95d2-ae342870fade")))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid table compaction request",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              examples =
                  @ExampleObject(
                      value = "Invalid option combination: Can not use split-output here")))
  @Operation(
      summary =
          "Force a (major) compaction on one or more tables or user-defined compaction on given SSTables",
      operationId = "compact")
  public Response compact(CompactRequest compactRequest) {
    return handle(
        () -> {
          // Retaining logic from org.apache.cassandra.tools.nodetool.Compact
          boolean tokenProvided =
              !(StringUtils.isBlank(compactRequest.startToken)
                  && StringUtils.isBlank(compactRequest.endToken));
          if (compactRequest.splitOutput && (compactRequest.userDefined || tokenProvided)) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("Invalid option combination: Can not use split-output here")
                .build();
          }
          if (compactRequest.userDefined && tokenProvided) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity(
                    "Invalid option combination: Can not provide tokens when using user-defined")
                .build();
          }

          if (compactRequest.userDefined) {
            try {
              if (CollectionUtils.isEmpty(compactRequest.userDefinedFiles)) {
                return Response.status(HttpStatus.SC_BAD_REQUEST)
                    .entity("Must provide a file if setting userDefined to true")
                    .build();
              }

              String userDefinedFiles = String.join(",", compactRequest.userDefinedFiles);
              return Response.accepted(
                      ResponseTools.getSingleRowStringResponse(
                          app.dbUnixSocketFile,
                          app.cqlService,
                          "CALL NodeOps.forceUserDefinedCompaction(?, ?)",
                          userDefinedFiles,
                          true))
                  .build();
            } catch (Exception e) {
              throw new RuntimeException("Error occurred during user defined compaction", e);
            }
          }

          List<String> tables = compactRequest.tables;
          if (CollectionUtils.isEmpty(tables)) {
            tables = new ArrayList<>();
          }

          String keyspaceName = compactRequest.keyspaceName;
          if (StringUtils.isBlank(keyspaceName)) {
            keyspaceName = "ALL";
          }

          if (tokenProvided) {
            return Response.accepted(
                    ResponseTools.getSingleRowStringResponse(
                        app.dbUnixSocketFile,
                        app.cqlService,
                        "CALL NodeOps.forceKeyspaceCompactionForTokenRange(?, ?, ?, ?, ?)",
                        keyspaceName,
                        compactRequest.startToken,
                        compactRequest.endToken,
                        tables,
                        true))
                .build();
          }
          return Response.accepted(
                  ResponseTools.getSingleRowStringResponse(
                      app.dbUnixSocketFile,
                      app.cqlService,
                      "CALL NodeOps.forceKeyspaceCompaction(?, ?, ?, ?)",
                      compactRequest.splitOutput,
                      keyspaceName,
                      tables,
                      true))
              .build();
        });
  }

  @GET
  @Path("/compactions")
  @Operation(summary = "Returns active compactions", operationId = "getCompactions")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(
      responseCode = "200",
      description = "Compactions",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              array = @ArraySchema(schema = @Schema(implementation = Compaction.class))))
  public Response getCompactions() {
    return handle(
        () -> {
          ResultSet result =
              app.cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.getCompactions()");
          Row row = result.one();
          assert row != null;
          List<Compaction> compactions =
              row.get(0, LIST_OF_MAP_OF_STRINGS).stream()
                  .map(Compaction::fromMap)
                  .collect(Collectors.toList());
          return Response.ok(compactions, MediaType.APPLICATION_JSON).build();
        });
  }

  @GET
  @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
  @ApiResponse(
      responseCode = "200",
      description = "Table list",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              array = @ArraySchema(schema = @Schema(implementation = Table.class))))
  @ApiResponse(
      responseCode = "400",
      description = "Keyspace name not provided",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples =
                  @ExampleObject(
                      value = "List tables failed. Non-empty 'keyspaceName' must be provided")))
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "List the table names in the given keyspace", operationId = "listTablesV1")
  public Response list(
      @Parameter(required = true) @QueryParam(value = "keyspaceName") String keyspaceName) {
    if (StringUtils.isBlank(keyspaceName)) {
      return Response.status(HttpStatus.SC_BAD_REQUEST)
          .entity("List tables failed. Non-empty 'keyspaceName' must be provided")
          .build();
    }
    return handle(
        () -> {
          ResultSet result =
              app.cqlService.executePreparedStatement(
                  app.dbUnixSocketFile, "CALL NodeOps.getTables(?)", keyspaceName);
          List<Table> tables = Lists.newArrayList();
          for (Row row : result) {
            tables.add(
                new Table(
                    row.getString("name"), row.getMap("compaction", String.class, String.class)));
          }
          return Response.ok(tables, MediaType.APPLICATION_JSON).build();
        });
  }

  @POST
  @Path("/garbagecollect")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "202",
      description = "Job ID for table garbage collection process",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "d69d1d95-9348-4460-95d2-ae342870fade")))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid table garbage collection request",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "tombstoneOption must be either ROW or CELL")))
  @Operation(
      summary = "Remove deleted data from one or more tables",
      operationId = "garbageCollect")
  public Response garbageCollect(
      @QueryParam(value = "tombstoneOption") String tombstoneOptionStr,
      KeyspaceRequest keyspaceRequest) {
    return handle(
        () -> {
          List<String> tables = keyspaceRequest.tables;
          if (CollectionUtils.isEmpty(tables)) {
            tables = new ArrayList<>();
          }

          String keyspaceName = keyspaceRequest.keyspaceName;
          if (StringUtils.isBlank(keyspaceName)) {
            keyspaceName = "ALL";
          } else if (!keyspaceExists(keyspaceName)) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("keyspace " + keyspaceName + " does not exists")
                .build();
          }

          String tombstoneOption = tombstoneOptionStr;
          if (StringUtils.isBlank(tombstoneOption)) {
            tombstoneOption = "ROW";
          }

          if (!StringUtils.equalsIgnoreCase("ROW", tombstoneOption)
              && !StringUtils.equalsIgnoreCase("CELL", tombstoneOption)) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("tombstoneOption must be either ROW or CELL")
                .build();
          }

          return Response.accepted(
                  ResponseTools.getSingleRowStringResponse(
                      app.dbUnixSocketFile,
                      app.cqlService,
                      "CALL NodeOps.garbageCollect(?, ?, ?, ?, ?)",
                      tombstoneOption,
                      keyspaceRequest.jobs,
                      keyspaceName,
                      tables,
                      true))
              .build();
        });
  }

  @POST
  @Path("/flush")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "202",
      description = "Job ID for table flush process",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "d69d1d95-9348-4460-95d2-ae342870fade")))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid flush request",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "keyspace sys-tem does not exists")))
  @Operation(summary = "Flush one or more tables", operationId = "flush")
  public Response flush(KeyspaceRequest keyspaceRequest) {
    return handle(
        () -> {
          List<String> tables = keyspaceRequest.tables;
          if (CollectionUtils.isEmpty(tables)) {
            tables = new ArrayList<>();
          }

          String keyspaceName = keyspaceRequest.keyspaceName;
          if (StringUtils.isBlank(keyspaceName)) {
            keyspaceName = "ALL";
          } else if (!keyspaceExists(keyspaceName)) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("keyspace " + keyspaceName + " does not exists")
                .build();
          }

          return Response.accepted(
                  ResponseTools.getSingleRowStringResponse(
                      app.dbUnixSocketFile,
                      app.cqlService,
                      "CALL NodeOps.forceKeyspaceFlush(?, ?, ?)",
                      keyspaceName,
                      tables,
                      true))
              .build();
        });
  }
}
