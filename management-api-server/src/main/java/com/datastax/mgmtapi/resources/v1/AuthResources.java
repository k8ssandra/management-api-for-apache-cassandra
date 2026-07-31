/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v1;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.v1.models.IdentityRequest;
import com.datastax.mgmtapi.resources.v1.models.IdentityToRoleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;

@Path("/api/v1/ops/auth/identity_to_role")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.TEXT_PLAIN)
public class AuthResources extends BaseResources {
  private static final String UNSUPPORTED_MESSAGE =
      "Identity-to-role operations are only supported on HCD 2.x and Cassandra 5.0 or newer";

  public AuthResources(ManagementApplication application) {
    super(application);
  }

  @POST
  @Operation(
      summary = "Creates or updates an identity-to-role mapping",
      operationId = "addIdentityToRole")
  @ApiResponse(
      responseCode = "200",
      description = "Identity-to-role mapping created",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(responseCode = "400", description = "Invalid input or unsupported Cassandra version")
  public Response addIdentityToRole(@RequestBody(required = true) IdentityToRoleRequest request) {
    return handle(
        () -> {
          if (request == null) {
            return emptyRequestBodyResponse();
          }

          if (StringUtils.isBlank(request.role) || StringUtils.isBlank(request.identity)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Role and identity are required")
                .build();
          }

          if (!isSupportedCassandraVersion()) {
            return unsupportedResponse();
          }

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.addIdentityToRole(?, ?)",
              request.identity,
              request.role);
          return Response.ok("OK").build();
        });
  }

  @DELETE
  @Operation(summary = "Deletes an identity-to-role mapping", operationId = "deleteIdentityToRole")
  @ApiResponse(
      responseCode = "200",
      description = "Identity-to-role mapping deleted",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(responseCode = "400", description = "Invalid input or unsupported Cassandra version")
  public Response deleteIdentityToRole(@RequestBody(required = true) IdentityRequest request) {
    return handle(
        () -> {
          if (request == null) {
            return emptyRequestBodyResponse();
          }

          if (StringUtils.isBlank(request.identity)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Identity is empty").build();
          }

          if (!isSupportedCassandraVersion()) {
            return unsupportedResponse();
          }

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile, "CALL NodeOps.deleteIdentityToRole(?)", request.identity);
          return Response.ok("OK").build();
        });
  }

  // I think some of these could be used elsewhere in the project also, but I'm not sure if there's
  // a common place
  // for these..

  private boolean isSupportedCassandraVersion() throws Exception {
    final String releaseVersion =
        ResponseTools.getSingleRowStringResponse(
            app.dbUnixSocketFile, app.cqlService, CASSANDRA_VERSION_CQL_STRING);
    if (releaseVersion == null) {
      return false;
    }

    try {
      int major = Integer.parseInt(releaseVersion.split("\\.", 2)[0]);
      return major >= 5;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private Response emptyRequestBodyResponse() {
    return Response.status(Response.Status.BAD_REQUEST).entity("Request body is empty").build();
  }

  private Response unsupportedResponse() {
    return Response.status(Response.Status.BAD_REQUEST).entity(UNSUPPORTED_MESSAGE).build();
  }
}
