/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.Job;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import static com.datastax.mgmtapi.resources.NodeOpsResources.handle;


@Path("/api/v0")
public class K8OperatorResources
{
    private static final Logger logger = LoggerFactory.getLogger(K8OperatorResources.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private final ManagementApplication app;
    private final CqlService cqlService;

    public K8OperatorResources(ManagementApplication application)
    {
        this.app = application;
        this.cqlService = application.cqlService;
    }

    @GET
    @Path("/probes/liveness")
    @Operation(summary = "Indicates whether this service is running", operationId = "checkLiveness")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Service is running",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            examples = @ExampleObject(
                value = "OK"
            )
        )
    )
    public Response checkLiveness()
    {
        return Response.ok("OK").build();
    }

    @GET
    @Path("/probes/readiness")
    @Operation(summary = "Indicates whether the Cassandra service is ready to service requests", operationId = "checkReadiness")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Service is ready to handle requests",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            examples = @ExampleObject(
                value = "OK"
            )
        )
    )
    @ApiResponse(responseCode = "500", description = "Service is not ready to handle requests")
    public Response checkReadiness()
    {
        return handle(() ->
        {
            ResultSet resultSet = cqlService.executeCql(app.dbUnixSocketFile, "SELECT * from system.local");
            Row result = resultSet.one();

            if (result != null)
            {
                return Response.ok("OK").build();
            }
            else
            {
                Response.ResponseBuilder rb = Response.status(Response.Status.INTERNAL_SERVER_ERROR);
                return rb.build();
            }
        });
    }

    @GET
    @Path("/probes/cluster")
    @Operation(summary = "Indicated whether the Cassandra cluster is able to achieve the specified consistency", operationId = "checkClusterConsistency")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @ApiResponse(responseCode = "200", description = "Cassandra is able to achieve the specified consistency")
    @ApiResponse(responseCode = "500", description = "Cassandra is unable to achieve the specified consistency",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                implementation = Map.class
            ),
            examples = @ExampleObject(
                value = "{\n" +
"    \"[-8842966077830801609, -7599446072150209635]\": [\n" +
"        \"2 replicas required, but only 1 nodes in the ring\"\n" +
"    ],\n" +
"    \"[-7599446072150209635, -6632081829071674136]\": [\n" +
"        \"2 replicas required, but only 1 nodes in the ring\"\n" +
"    ]}"
            )
        )
    )
    public Response checkClusterConsistency(@QueryParam(value="consistency_level")String consistencyLevelStr, @QueryParam(value = "rf_per_dc") Integer rfPerDcVal)
    {
        return handle(() ->
        {
            String consistencyLevel = consistencyLevelStr;
            if (consistencyLevel == null)
                consistencyLevel = "LOCAL_QUORUM";

            Integer rfPerDc = rfPerDcVal;
            if (rfPerDc == null)
                rfPerDc = 3;

            ResultSet result = cqlService.executePreparedStatement(app.dbUnixSocketFile,
                    "CALL NodeOps.checkConsistencyLevel(?, ?)", consistencyLevel, rfPerDc);

            Map<List, List> response = result.one().getMap("result", List.class, List.class);

            if (response.isEmpty())
                return Response.ok().build();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(jsonMapper.writeValueAsString(response))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        });
    }

    @POST
    @Path("/ops/seeds/reload")
    @Operation(summary = "Reload the seed node list from the seed provider", operationId = "seedReload")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponse(responseCode = "200", description = "List of new seeds after reload",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = String.class),
            examples = @ExampleObject(value = "[127.0.0.1, 127.0.0.2]")
        )
    )
    public Response seedReload()
    {
        return handle(() ->
        {
            ResultSet result = cqlService.executeCql(app.dbUnixSocketFile, "CALL NodeOps.reloadSeeds()");

            List<String> response = result.one().getList("result", String.class);

            return Response.ok(jsonMapper.writeValueAsString(response), MediaType.APPLICATION_JSON).build();
        });
    }

    @GET
    @Path("/ops/executor/job")
    @Operation(summary = "Returns Job details for the supplied Job ID", operationId = "getJobStatus")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @ApiResponse(responseCode = "200", description = "Map of job details",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = Job.class)
        )
    )
    @ApiResponse(responseCode = "404", description = "Job not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = Job.class)
        )
    )
    public Response getJobStatus(@Parameter(required = true) @QueryParam(value="job_id") String jobId) {
        return handle(() -> {
            Map<String, String> jobResponse = (Map<String, String>) ResponseTools.getSingleRowResponse(app.dbUnixSocketFile, cqlService, "CALL NodeOps.jobStatus(?)", jobId);
            if(jobResponse.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).entity(jobResponse).build();
            }
            return Response.ok(jobResponse, MediaType.APPLICATION_JSON).build();
        });
    }
}
