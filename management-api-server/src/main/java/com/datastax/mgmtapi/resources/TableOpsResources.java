/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.models.CompactRequest;
import com.datastax.mgmtapi.resources.models.CreateTableRequest;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import com.datastax.mgmtapi.resources.models.ScrubRequest;
import com.datastax.oss.driver.api.core.cql.ResultSet;
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
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

@Path("/api/v0/ops/tables")
public class TableOpsResources extends BaseResources {

  public TableOpsResources(ManagementApplication application) {
    super(application);
  }

  @POST
  @Path("/scrub")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Table scrub successful",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @Operation(summary = "Scrub (rebuild sstables for) one or more tables", operationId = "scrub")
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

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.scrub(?, ?, ?, ?, ?, ?, ?, ?)",
              scrubRequest.disableSnapshot,
              scrubRequest.skipCorrupted,
              scrubRequest.checkData,
              scrubRequest.reinsertOverflowedTTL,
              scrubRequest.jobs,
              keyspaceName,
              tables,
              false);

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/sstables/upgrade")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "SSTable upgrade successful",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @Operation(
      summary =
          "Rewrite sstables (for the requested tables) that are not on the current version (thus upgrading them to said current version)",
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

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.upgradeSSTables(?, ?, ?, ?, ?)",
              keyspaceName,
              !excludeCurrentVersion,
              keyspaceRequest.jobs,
              tables,
              false);

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/compact")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Table compaction successful",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid table compaction request",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
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
              app.cqlService.executePreparedStatement(
                  app.dbUnixSocketFile,
                  "CALL NodeOps.forceUserDefinedCompaction(?, ?)",
                  userDefinedFiles,
                  false);
            } catch (Exception e) {
              throw new RuntimeException("Error occurred during user defined compaction", e);
            }
            return Response.ok("OK").build();
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
            app.cqlService.executePreparedStatement(
                app.dbUnixSocketFile,
                "CALL NodeOps.forceKeyspaceCompactionForTokenRange(?, ?, ?, ?, ?)",
                keyspaceName,
                compactRequest.startToken,
                compactRequest.endToken,
                tables,
                false);
          } else {
            app.cqlService.executePreparedStatement(
                app.dbUnixSocketFile,
                "CALL NodeOps.forceKeyspaceCompaction(?, ?, ?, ?)",
                compactRequest.splitOutput,
                keyspaceName,
                tables,
                false);
          }

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/garbagecollect")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Table garbage collection successful",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
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

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.garbageCollect(?, ?, ?, ?, ?)",
              tombstoneOption,
              keyspaceRequest.jobs,
              keyspaceName,
              tables,
              false);

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/flush")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Table flush successful",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
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
          }

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.forceKeyspaceFlush(?, ?, ?)",
              keyspaceName,
              tables,
              false);

          return Response.ok("OK").build();
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
              array = @ArraySchema(schema = @Schema(implementation = String.class)),
              examples = @ExampleObject(value = "[\n    \"table_1\",\n    \"table_2\"\n]")))
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
  @Operation(summary = "List the table names in the given keyspace", operationId = "listTables")
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
          List<String> tables =
              result.all().stream().map(row -> row.getString("name")).collect(Collectors.toList());
          return Response.ok(tables, MediaType.APPLICATION_JSON).build();
        });
  }

  @POST
  @Path("/create")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Table creation successful",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Table creation failed",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "Table creation failed: some failure message")))
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Create a new table in an existing keyspace", operationId = "createTable")
  public Response create(CreateTableRequest request) {
    try {
      request.validate();
    } catch (RuntimeException e) {
      return Response.status(HttpStatus.SC_BAD_REQUEST)
          .entity("Table creation failed: " + e.getMessage())
          .build();
    }
    return handle(
        () -> {
          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.createTable(?, ?, ?, ?, ?, ?, ?, ?, ?)",
              request.keyspaceName,
              request.tableName,
              request.columnNamesAndTypes(),
              request.partitionKeyColumnNames(),
              request.clusteringColumnNames(),
              request.clusteringOrders(),
              request.staticColumnNames(),
              request.simpleOptions(),
              request.complexOptions());

          return Response.ok("OK").build();
        });
  }
}
