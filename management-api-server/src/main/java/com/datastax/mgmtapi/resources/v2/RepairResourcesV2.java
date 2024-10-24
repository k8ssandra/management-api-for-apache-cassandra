/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v2;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.v2.models.RepairParallelism;
import com.datastax.mgmtapi.resources.v2.models.RepairRequest;
import com.datastax.mgmtapi.resources.v2.models.RepairRequestResponse;
import com.datastax.mgmtapi.resources.v2.models.RingRange;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.annotations.VisibleForTesting;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/api/v2/repairs")
public class RepairResourcesV2 extends BaseResources {

  public RepairResourcesV2(ManagementApplication application) {
    super(application);
  }

  @PUT
  @Operation(summary = "Initiate a new repair", operationId = "putRepairV2")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes("application/json")
  @ApiResponse(
      responseCode = "202",
      description = "Repair Successfully requested",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = RepairRequestResponse.class),
              examples = @ExampleObject(value = "Accepted")))
  @ApiResponse(
      responseCode = "400",
      description = "Repair request missing Keyspace name",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = Response.Status.class),
              examples = @ExampleObject(value = "keyspace must be specified")))
  @ApiResponse(
      responseCode = "500",
      description = "internal error, we did not receive the expected repair ID from Cassandra.",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = Response.Status.class),
              examples =
                  @ExampleObject(
                      value =
                          "internal error, we did not receive the expected repair ID from Cassandra.")))
  public final Response repair(RepairRequest request) {
    return handle(
        () -> {
          if (request.keyspace == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("keyspaceName must be specified")
                .build();
          }

          ResultSet res =
              app.cqlService.executePreparedStatement(
                  app.dbUnixSocketFile,
                  "CALL NodeOps.repair(?, ?, ?, ?, ?, ?, ?, ?)",
                  request.keyspace,
                  request.tables,
                  request.fullRepair,
                  true,
                  getParallelismName(request.repairParallelism),
                  request.datacenters,
                  getRingRangeString(request.associatedTokens),
                  request.repairThreadCount);
          try {
            Row row = res.one();
            String repairID = row.getString(0);
            return Response.accepted(new RepairRequestResponse(repairID)).build();
          } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Repair request failed: " + e.getMessage())
                .build();
          }
        });
  }

  @DELETE
  @Operation(summary = "Cancel all repairs", operationId = "deleteRepairsV2")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(
      responseCode = "202",
      description = "Cancel repairs Successfully requested",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = RepairRequestResponse.class),
              examples = @ExampleObject(value = "Accepted")))
  public Response cancelAllRepairs() {
    return handle(
        () -> {
          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile, "CALL NodeOps.stopAllRepairs()");
          return Response.accepted().build();
        });
  }

  private String getParallelismName(RepairParallelism parallelism) {
    return parallelism != null ? parallelism.getName() : null;
  }

  @VisibleForTesting
  String getRingRangeString(List<RingRange> associatedTokens) {
    if (associatedTokens == null || associatedTokens.isEmpty()) {
      return null;
    }
    return associatedTokens.stream().map(i -> toRangeString(i)).collect(Collectors.joining(","));
  }

  private String toRangeString(RingRange ringRange) {
    return String.join(":", ringRange.start.toString(), ringRange.end.toString());
  }
}
