/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import io.swagger.v3.oas.annotations.Operation;

import static com.datastax.mgmtapi.resources.NodeOpsResources.handle;

@Path("/api/v0/metadata")
public class MetadataResources
{
    private static final Logger logger = LoggerFactory.getLogger(MetadataResources.class);

    private final ManagementApplication app;
    private final CqlService cqlService;

    public MetadataResources(ManagementApplication application) {
        this.app = application;
        this.cqlService = application.cqlService;
    }

    @GET
    @Path("/versions/release")
    @Operation(summary = "Returns the Release Version")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getReleaseVersion()
    {
        return executeWithStringResponse("CALL NodeOps.getReleaseVersion()");
    }

    /**
     * Executes a CQL query with the expectation that there will be a single row returned with type String
     *
     * @param query CQL query to execute
     *
     * @return Returns a Response with status code 200 and body of query response on success and status code 500 on failure
     */
    private Response executeWithStringResponse(String query)
    {
        return handle(() ->
        {
            ResultSet rs = cqlService.executeCql(app.dseUnixSocketFile, query);

            Row row = rs.one();
            String queryResponse = null;
            if (row != null)
            {
                queryResponse = row.getString(0);
            }

            return Response.ok(queryResponse).build();
        });
    }
}
