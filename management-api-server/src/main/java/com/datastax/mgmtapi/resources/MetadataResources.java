/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.FeatureSet;
import org.apache.http.ConnectionClosedException;
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
    private static final String CASSANDRA_VERSION_CQL_STRING = "CALL NodeOps.getReleaseVersion()";

    private final ManagementApplication app;
    private final CqlService cqlService;

    public MetadataResources(ManagementApplication application) {
        this.app = application;
        this.cqlService = application.cqlService;
    }

    @GET
    @Path("/versions/release")
    @Operation(summary = "Returns the Cassandra release version")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getReleaseVersion()
    {
        return executeWithStringResponse(CASSANDRA_VERSION_CQL_STRING);
    }

    @GET
    @Path("/endpoints")
    @Operation(summary = "Returns this nodes view of the endpoint states of nodes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEndpointStates()
    {
        return executeWithJSONResponse("CALL NodeOps.getEndpointStates()");
    }

    @GET
    @Path("/localdc")
    @Operation(summary = "Returns the DataCenter the local node belongs to")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getLocalDataCenter()
    {
        return executeWithStringResponse("CALL NodeOps.getLocalDataCenter()");
    }

    @GET
    @Path("/versions/features")
    @Operation(summary = "Returns the management-api featureSet")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFeatureSet() {
        return handle(() -> {
            String cassandraVersion = ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService, CASSANDRA_VERSION_CQL_STRING);
            // TODO management-api-release-version is not included in the release packages
            FeatureSet featureSet = new FeatureSet(cassandraVersion, "");
            return Response.ok(featureSet).build();
        });
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
                Response.ok(ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService, query)).build());
    }

    /**
     * Executes a CQL query with the expectation that there will be a single row returned with type String
     *
     * @param query CQL query to execute
     *
     * @return Returns a Response with status code 200 and body of query response on success and status code 500 on failure
     */
    private Response executeWithJSONResponse(String query)
    {
        return handle(() ->
                Response.ok(Entity.json(ResponseTools.getSingleRowResponse(app.dbUnixSocketFile, cqlService, query))).build());
    }
}
