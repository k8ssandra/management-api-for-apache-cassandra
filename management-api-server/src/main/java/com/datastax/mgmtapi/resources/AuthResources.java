/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import static com.datastax.mgmtapi.resources.NodeOpsResources.handle;
import javax.ws.rs.Produces;

@Path("/api/v0/ops/auth")
public class AuthResources
{
    private final ManagementApplication app;
    private final CqlService cqlService;

    public AuthResources(ManagementApplication application)
    {
        this.app = application;
        this.cqlService = application.cqlService;
    }

    @POST
    @Path("/role/")
    @Operation(summary = "Creates a new user role", operationId = "createRole")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Role created",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            examples = @ExampleObject(
                value = "OK"
            )
        )
    )
    @ApiResponse(responseCode = "400", description = "Username and/or password is empty",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            examples = @ExampleObject(
                value = "Username is empty"
            )
        )
    )
    public Response createRole(
            @QueryParam(value = "username")String name,
            @QueryParam(value = "is_superuser") Boolean isSuperUser,
            @QueryParam(value = "can_login") Boolean canLogin,
            @QueryParam(value = "password") String password)
    {
        return handle(() -> {

            if (StringUtils.isBlank(name))
                return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "Username is empty").build();

            if (StringUtils.isBlank(password))
                return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "Password is empty").build();

            cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.createRole(?,?,?,?)", name, isSuperUser, canLogin, password);

            return Response.ok("OK").build();
        });
    }


}
