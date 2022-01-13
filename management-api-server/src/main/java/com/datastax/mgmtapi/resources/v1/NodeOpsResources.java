package com.datastax.mgmtapi.resources.v1;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.oss.driver.api.core.cql.Row;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import javax.ws.rs.*;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.datastax.mgmtapi.resources.NodeOpsResources.handle;

@Path("/api/v1/ops/node")
public class NodeOpsResources {

    private final ManagementApplication app;
    private final CqlService cqlService;

    public NodeOpsResources(ManagementApplication application) {
        this.app = application;
        this.cqlService = application.cqlService;
    }

    @POST
    @Path("/decommission")
    @Operation(summary = "Decommission the *node I am connecting to*. This invocation returns immediately and returns a job id.", operationId = "decommission_v1")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "202", description = "Job ID for successfully scheduled Cassandra node decommission request",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "34034d36-3c1e-4bdb-8a8f-f92291a64cb3")
        )
    )
    public Response decommission(@QueryParam(value="force")boolean force)
    {
        return handle(() ->
                Response.accepted(
                        ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService,"CALL NodeOps.decommission(?, ?)", force, true)
                        ).build());
    }

    @POST
    @Path("/rebuild")
    @Operation(summary = "Rebuild data by streaming data from other nodes. This operation returns immediately with a job id.", operationId = "rebuild_v1")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "202", description = "Job ID for successfully scheduled Cassandra node rebuild request",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "34034d36-3c1e-4bdb-8a8f-f92291a64cb3")
        )
    )
    public Response rebuild(@QueryParam("src_dc") String srcDatacenter)
    {
        return handle(() ->
                Response.accepted(
                        ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService, "CALL NodeOps.rebuild(?)", srcDatacenter)
                ).build());
    }

    @GET
    @Path("/schema/versions")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponse(responseCode = "200", description = "Gets the schema versions for each node. Useful for checking schema agreement")
    @Operation(summary = "Get schema versions.", operationId = "getSchemaVersions")
    public Response schemaVersions()
    {
        return handle(() ->
        {
            Row row = cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.getSchemaVersions()").one();

            Object queryResponse = null;
            if (row != null)
            {
                queryResponse = row.getObject(0);
            }
            return Response.ok(Entity.json(queryResponse)).build();
        });
    }

}
