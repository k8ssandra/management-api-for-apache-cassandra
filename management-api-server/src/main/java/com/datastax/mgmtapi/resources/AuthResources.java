/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import static com.datastax.mgmtapi.resources.v1.TableOpsResources.LIST_OF_MAP_OF_STRINGS;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

@Path("/api/v0/ops/auth")
public class AuthResources extends BaseResources {
  private static final ObjectMapper jsonMapper = new ObjectMapper();

  public AuthResources(ManagementApplication application) {
    super(application);
  }

  @POST
  @Path("/role")
  @Operation(summary = "Creates a new user role", operationId = "createRole")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Role created",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Username and/or password is empty",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "Username is empty")))
  public Response createRole(
      @QueryParam(value = "username") String name,
      @QueryParam(value = "is_superuser") Boolean isSuperUser,
      @QueryParam(value = "can_login") Boolean canLogin,
      @QueryParam(value = "password") String password) {
    return handle(
        () -> {
          if (StringUtils.isBlank(name))
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "Username is empty")
                .build();

          if (StringUtils.isBlank(password))
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "Password is empty")
                .build();

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile,
              "CALL NodeOps.createRole(?,?,?,?)",
              name,
              isSuperUser,
              canLogin,
              password);

          return Response.ok("OK").build();
        });
  }

  @DELETE
  @Path("/role")
  @Operation(summary = "Drops a user role", operationId = "dropRole")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Role dropped",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Username is empty",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "Username is empty")))
  public Response dropRole(@QueryParam(value = "username") String name) {
    return handle(
        () -> {
          if (StringUtils.isBlank(name))
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "Username is empty")
                .build();

          app.cqlService.executePreparedStatement(
              app.dbUnixSocketFile, "CALL NodeOps.dropRole(?)", name);

          return Response.ok("OK").build();
        });
  }

  @GET
  @Path("/role")
  @Operation(summary = "Lists existing user rules", operationId = "listRoles")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "List of roles",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "400",
      description = "Username is empty",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "Username is empty")))
  public Response listRoles() {
    return handle(
        () -> {
          ResultSet result =
              app.cqlService.executePreparedStatement(
                  app.dbUnixSocketFile, "CALL NodeOps.listRoles()");
          Row row = result.one();
          if (row != null) {
            List<Map<String, String>> roles = row.get(0, LIST_OF_MAP_OF_STRINGS);

            return Response.ok(jsonMapper.writeValueAsString(roles), MediaType.APPLICATION_JSON)
                .build();
          }

          return Response.status(HttpStatus.SC_NO_CONTENT).build();
        });
  }
}
