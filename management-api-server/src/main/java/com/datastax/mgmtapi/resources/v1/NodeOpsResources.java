package com.datastax.mgmtapi.resources.v1;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import io.swagger.v3.oas.annotations.Operation;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
    @Operation(summary = "Decommission the *node I am connecting to*. This invocation returns immediately and returns a job id.")
    @Produces(MediaType.TEXT_PLAIN)
    public Response decommission(@QueryParam(value="force")boolean force)
    {
        return com.datastax.mgmtapi.resources.NodeOpsResources.handle(() ->
                Response.accepted(
                        ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService,"CALL NodeOps.decommission(?, ?)", force, true)
                        ).build());
    }

}
