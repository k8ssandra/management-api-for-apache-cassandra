package com.datastax.mgmtapi.resources.v1;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
    @ApiResponse(responseCode = "202", description = "Job ID for successfully scheduled Cassandra node decommission request",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "34034d36-3c1e-4bdb-8a8f-f92291a64cb3")
        )
    )
    public Response decommission(@QueryParam(value="force")boolean force)
    {
        return com.datastax.mgmtapi.resources.NodeOpsResources.handle(() ->
                Response.accepted(
                        ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService,"CALL NodeOps.decommission(?, ?)", force, true)
                        ).build());
    }

    @POST
    @Path("/rebuild")
    @Operation(summary = "Rebuild data by streaming data from other nodes. This operation returns immediately with a job id.", operationId = "rebuild_v1")
    @ApiResponse(responseCode = "202", description = "Job ID for successfully scheduled Cassandra node rebuild request",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "34034d36-3c1e-4bdb-8a8f-f92291a64cb3")
        )
    )
    public Response rebuild(@QueryParam("src_dc") String srcDatacenter)
    {
        return com.datastax.mgmtapi.resources.NodeOpsResources.handle(() ->
                Response.accepted(
                        ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService, "CALL NodeOps.rebuild(?)", srcDatacenter)
                ).build());
    }

}
