/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v1;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.RepairRequest;
import com.datastax.oss.driver.api.core.cql.Row;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Path("/api/v1/ops/node")
public class NodeOpsResources extends BaseResources {

  public NodeOpsResources(ManagementApplication application) {
    super(application);
  }

  @POST
  @Path("/decommission")
  @Operation(
      summary =
          "Decommission the *node I am connecting to*. This invocation returns immediately and returns a job id.",
      operationId = "decommission_v1")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "202",
      description = "Job ID for successfully scheduled Cassandra node decommission request",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "34034d36-3c1e-4bdb-8a8f-f92291a64cb3")))
  public Response decommission(@QueryParam(value = "force") boolean force) {
    return handle(
        () ->
            Response.accepted(
                    ResponseTools.getSingleRowStringResponse(
                        app.dbUnixSocketFile,
                        app.cqlService,
                        "CALL NodeOps.decommission(?, ?)",
                        force,
                        true))
                .build());
  }

  @POST
  @Path("/rebuild")
  @Operation(
      summary =
          "Rebuild data by streaming data from other nodes. This operation returns immediately with a job id.",
      operationId = "rebuild_v1")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "202",
      description = "Job ID for successfully scheduled Cassandra node rebuild request",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "34034d36-3c1e-4bdb-8a8f-f92291a64cb3")))
  public Response rebuild(@QueryParam("src_dc") String srcDatacenter) {
    return handle(
        () ->
            Response.accepted(
                    ResponseTools.getSingleRowStringResponse(
                        app.dbUnixSocketFile,
                        app.cqlService,
                        "CALL NodeOps.rebuild(?)",
                        srcDatacenter))
                .build());
  }

  @GET
  @Path("/schema/versions")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(
      responseCode = "200",
      description = "Gets the schema versions for each node. Useful for checking schema agreement",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              // this should actually be a Response class object, but we don't have one. And a
              // generic Map implementation here jsut results in a String typoe in the openAPI doc.
              schema = @Schema(implementation = Map.class),
              examples =
                  @ExampleObject(
                      value =
                          "{2207c2a9-f598-3971-986b-2926e09e239d: [10.244.1.4, 10.244.2.3, 10.244.3.3]}")))
  @Operation(summary = "Get schema versions.", operationId = "getSchemaVersions")
  public Response schemaVersions() {
    return handle(
        () -> {
          Row row =
              app.cqlService
                  .executePreparedStatement(
                      app.dbUnixSocketFile, "CALL NodeOps.getSchemaVersions()")
                  .one();

          Map<String, List> schemaVersions = Collections.emptyMap();
          if (row != null) {
            schemaVersions = row.getMap(0, String.class, List.class);
          }
          return Response.ok(schemaVersions).build();
        });
  }

  @POST
  @Path("/repair")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "202",
      description = "Job ID for successfully scheduled Cassandra repair request",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "repair-1234567")))
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
          return Response.accepted(
                  ResponseTools.getSingleRowStringResponse(
                      app.dbUnixSocketFile,
                      app.cqlService,
                      "CALL NodeOps.repair(?, ?, ?, ?, ?, ?, ?, ?)",
                      repairRequest.keyspaceName,
                      repairRequest.tables,
                      repairRequest.full,
                      true,
                      null,
                      null,
                      null,
                      null))
              .build();
        });
  }
}
