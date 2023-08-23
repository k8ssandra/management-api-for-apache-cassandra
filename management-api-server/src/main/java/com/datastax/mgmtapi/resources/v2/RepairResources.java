package com.datastax.mgmtapi.resources.v2;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.v2.models.RepairRequest;
import com.datastax.mgmtapi.resources.v2.models.RepairRequestResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Path("/api/v2/repairs")
public class RepairResources extends BaseResources {

private static final ObjectMapper jsonMapper = new ObjectMapper();

  public RepairResources(ManagementApplication application) {
    super(application);
  }

  @PUT
  @Operation(summary = "Initiate a new repair", operationId = "v2PutRepair")
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
  public final Response repair(RepairRequest request) {
    return handle(
        () -> {
          if (request.keyspace == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("keyspaceName must be specified")
                .build();
          }
          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.repair(?, ?, ?)",
              repairRequest.keyspaceName,
              repairRequest.tables,
              repairRequest.full);

          return Response.ok("OK").build();
        });

    String repairID = ""; // TODO: implement me
    return Response.ok(new RepairRequestResponse(repairID)).build();
  }


}