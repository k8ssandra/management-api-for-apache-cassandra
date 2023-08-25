/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v2;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.v2.models.RepairRequest;
import com.datastax.mgmtapi.resources.v2.models.RepairRequestResponse;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Collection;
import java.util.Optional;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
      responseCode = "200",
      description = "Repair Successfully requested",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = RepairRequestResponse.class),
              examples = @ExampleObject(value = "OK")))
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
                  sanitise(request.tables),
                  request.fullRepair,
                  request.notifications,
                  sanitise(request.repairParallelism),
                  sanitise(request.datacenters),
                  sanitise(request.associatedTokens),
                  sanitise(request.repairThreadCount));
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

  // sanitise checks for empty collections and null values and returns empty Optionals when they are
  // found.
  private <T> Optional<T> sanitise(T o) {
    if (o instanceof Collection<?>) {
      if (((Collection) o).isEmpty()) {
        return Optional.empty();
      }
    }
    return Optional.ofNullable(o);
  }
}
