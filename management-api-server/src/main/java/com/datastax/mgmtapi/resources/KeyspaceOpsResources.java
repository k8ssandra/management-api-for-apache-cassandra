/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.CreateOrAlterKeyspaceRequest;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Path("/api/v0/ops/keyspace")
public class KeyspaceOpsResources extends BaseResources {
  private static final ObjectMapper jsonMapper = new ObjectMapper();

  public KeyspaceOpsResources(ManagementApplication application) {
    super(application);
  }

  @POST
  @Path("/cleanup")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
      summary =
          "Triggers the immediate cleanup of keys no longer belonging to a node. By default, clean all keyspaces. This operation is blocking and will return the executed job after finishing.",
      operationId = "cleanup")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Job ID for keyspace cleanup process",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "d69d1d95-9348-4460-95d2-ae342870fade")))
  public Response cleanup(KeyspaceRequest keyspaceRequest) {
    return handle(
        () -> {
          List<String> tables = keyspaceRequest.tables;
          if (CollectionUtils.isEmpty(tables)) {
            tables = new ArrayList<>();
          }

          String keyspaceName = keyspaceRequest.keyspaceName;
          if (StringUtils.isBlank(keyspaceName)) {
            keyspaceName = "NON_LOCAL_STRATEGY";
          }

          if (StringUtils.equalsAnyIgnoreCase(keyspaceName, "system", "system_schema")) {
            return Response.ok("OK").build();
          }

          return Response.ok(
                  ResponseTools.getSingleRowStringResponse(
                      app.dbUnixSocketFile,
                      app.cqlService,
                      "CALL NodeOps.forceKeyspaceCleanup(?, ?, ?, ?)",
                      keyspaceRequest.jobs,
                      keyspaceName,
                      tables,
                      false))
              .build();
        });
  }

  @POST
  @Path("/refresh")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "SSTables loaded successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Keyspace name or Table name not provided",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "Must provide a keyspace name")))
  @Operation(
      summary = "Load newly placed SSTables to the system without restart",
      operationId = "refresh")
  public Response refresh(
      @QueryParam(value = "keyspaceName") String keyspaceName,
      @QueryParam(value = "table") String table) {
    return handle(
        () -> {
          if (StringUtils.isBlank(keyspaceName)) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("Must provide a keyspace name")
                .build();
          }

          if (StringUtils.isBlank(table)) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("table must be provided")
                .build();
          }

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile, "CALL NodeOps.loadNewSSTables(?, ?)", keyspaceName, table);

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/create")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Keyspace created successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Keyspace name or Replication Settings not provided",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples =
                  @ExampleObject(
                      value =
                          "Keyspace creation failed. Non-empty 'keyspace_name' must be provided")))
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Create a new keyspace with the given name and replication settings",
      operationId = "createKeyspace")
  public Response create(CreateOrAlterKeyspaceRequest createOrAlterKeyspaceRequest) {
    return handle(
        () -> {
          if (StringUtils.isBlank(createOrAlterKeyspaceRequest.keyspaceName)) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("Keyspace creation failed. Non-empty 'keyspace_name' must be provided")
                .build();
          }

          if (null == createOrAlterKeyspaceRequest.replicationSettings
              || createOrAlterKeyspaceRequest.replicationSettings.isEmpty()) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("Keyspace creation failed. 'replication_settings' must be provided")
                .build();
          }

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.createKeyspace(?, ?)",
              createOrAlterKeyspaceRequest.keyspaceName,
              createOrAlterKeyspaceRequest.replicationSettingsAsMap());

          return Response.ok("OK").build();
        });
  }

  @POST
  @Path("/alter")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Keyspace Replication Settings altered successfully",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Keyspace name or Replication Settings not provided",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples =
                  @ExampleObject(
                      value =
                          "Altering Keyspace failed. Non-empty 'keyspace_name' must be provided")))
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Alter the replication settings of an existing keyspace",
      operationId = "alterKeyspace")
  public Response alter(CreateOrAlterKeyspaceRequest createOrAlterKeyspaceRequest) {
    return handle(
        () -> {
          if (StringUtils.isBlank(createOrAlterKeyspaceRequest.keyspaceName)) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("Altering Keyspace failed. Non-empty 'keyspace_name' must be provided")
                .build();
          }

          if (null == createOrAlterKeyspaceRequest.replicationSettings
              || createOrAlterKeyspaceRequest.replicationSettings.isEmpty()) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("Altering Keyspace failed. 'replication_settings' must be provided")
                .build();
          }

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.alterKeyspace(?, ?)",
              createOrAlterKeyspaceRequest.keyspaceName,
              createOrAlterKeyspaceRequest.replicationSettingsAsMap());

          return Response.ok("OK").build();
        });
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(
      responseCode = "200",
      description = "List of Keyspaces",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              array = @ArraySchema(schema = @Schema(implementation = String.class)),
              examples =
                  @ExampleObject(
                      value =
                          "[\n    \"system_schema\",\n    \"system\",\n    \"system_auth\",\n    \"system_distributed\",\n    \"system_traces\"\n]")))
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "List the keyspaces existing in the cluster", operationId = "listKeyspaces")
  public Response list(@QueryParam(value = "keyspaceName") String keyspaceName) {
    return handle(
        () -> {
          ResultSet result =
              app.cqlService.executePreparedStatement(
                  app.dbUnixSocketFile, "CALL NodeOps.getKeyspaces()");
          Row row = result.one();
          List<String> keyspaces = null;
          if (row != null) {
            List<String> allKeyspaces = row.getList(0, String.class);
            keyspaces =
                allKeyspaces.stream()
                    .filter(ks -> ks.equals(keyspaceName) || StringUtils.isBlank(keyspaceName))
                    .collect(Collectors.toList());
          }

          return Response.ok(jsonMapper.writeValueAsString(keyspaces), MediaType.APPLICATION_JSON)
              .build();
        });
  }

  @GET
  @Path("/replication")
  @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
  @ApiResponse(
      responseCode = "200",
      description = "Keyspace Replication Settings",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = String.class),
              examples =
                  @ExampleObject(
                      value =
                          "{\n    \"class\": \"org.apache.cassandra.locator.SimpleStrategy\",\n    \"replication_factor\": \"2\"\n}")))
  @ApiResponse(
      responseCode = "400",
      description = "Keyspace name not provided",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples =
                  @ExampleObject(
                      value =
                          "Get keyspace replication failed. Non-empty 'keyspaceName' must be provided")))
  @ApiResponse(
      responseCode = "404",
      description = "Keyspace name not found",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples =
                  @ExampleObject(
                      value =
                          "Get keyspace replication failed. Keyspace 'my_keyspace' does not exist.")))
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Get the replication settings of an existing keyspace",
      operationId = "replication")
  public Response getReplication(
      @Parameter(required = true) @QueryParam(value = "keyspaceName") String keyspaceName) {
    if (StringUtils.isBlank(keyspaceName)) {
      return Response.status(HttpStatus.SC_BAD_REQUEST)
          .entity("Get keyspace replication failed. Non-empty 'keyspaceName' must be provided")
          .build();
    }
    return handle(
        () -> {
          ResultSet result =
              app.cqlService.executePreparedStatement(
                  app.dbUnixSocketFile, "CALL NodeOps.getReplication(?)", keyspaceName);
          Row row = result.one();
          if (row == null) {
            return Response.status(HttpStatus.SC_NOT_FOUND)
                .entity(
                    String.format(
                        "Get keyspace replication failed. Keyspace '%s' does not exist.",
                        keyspaceName))
                .build();
          } else {
            Map<String, String> replication = row.getMap(0, String.class, String.class);
            assert replication != null;
            return Response.ok(replication, MediaType.APPLICATION_JSON).build();
          }
        });
  }
}
