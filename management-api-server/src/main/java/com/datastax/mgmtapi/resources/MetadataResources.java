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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

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
    @Operation(summary = "Returns the Cassandra release version", operationId = "getReleaseVersion")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Cassandra version'",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(
                implementation = String.class
            ),
            examples = @ExampleObject(
                value = "4.0.1"
            )
        )
    )
    public Response getReleaseVersion()
    {
        return executeWithStringResponse(CASSANDRA_VERSION_CQL_STRING);
    }

    @GET
    @Path("/endpoints")
    @Operation(summary = "Returns this nodes view of the endpoint states of nodes", operationId = "getEndpointStates")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponse(responseCode = "200", description = "Endpoint states'",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                implementation = String.class
            ),
            examples = @ExampleObject(
                value = ENDPOINTS_RESPONSE_EXAMPLE
            )
        )
    )
    public Response getEndpointStates()
    {
        return executeWithJSONResponse("CALL NodeOps.getEndpointStates()");
    }

    @GET
    @Path("/localdc")
    @Operation(summary = "Returns the DataCenter the local node belongs to", operationId = "getLocalDataCenter")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "200", description = "Local datacenter'",
        content = @Content(
            mediaType = MediaType.TEXT_PLAIN,
            schema = @Schema(
                implementation = String.class
            ),
            examples = @ExampleObject(
                value = "datacenter1"
            )
        )
    )
    public Response getLocalDataCenter()
    {
        return executeWithStringResponse("CALL NodeOps.getLocalDataCenter()");
    }

    @GET
    @Path("/versions/features")
    @Operation(summary = "Returns the management-api featureSet", operationId = "getFeatureSet")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponse(responseCode = "200", description = "Local datacenter'",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = FeatureSet.class)
        )
    )
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
    
    private static final String ENDPOINTS_RESPONSE_EXAMPLE = "{\n" +
"    \"entity\": [\n" +
"        {\n" +
"            \"DC\": \"datacenter1\",\n" +
"            \"ENDPOINT_IP\": \"172.17.0.2\",\n" +
"            \"HOST_ID\": \"5d2318b0-cfec-4697-8ed2-014b5f434ae8\",\n" +
"            \"IS_ALIVE\": \"true\",\n" +
"            \"LOAD\": \"135527.0\",\n" +
"            \"NATIVE_ADDRESS_AND_PORT\": \"172.17.0.2:9042\",\n" +
"            \"NET_VERSION\": \"12\",\n" +
"            \"RACK\": \"rack1\",\n" +
"            \"RELEASE_VERSION\": \"4.0.1\",\n" +
"            \"RPC_ADDRESS\": \"172.17.0.2\",\n" +
"            \"RPC_READY\": \"true\",\n" +
"            \"SCHEMA\": \"2207c2a9-f598-3971-986b-2926e09e239d\",\n" +
"            \"SSTABLE_VERSIONS\": \"big-nb\",\n" +
"            \"STATUS\": \"NORMAL,-2444908528344618126\",\n" +
"            \"STATUS_WITH_PORT\": \"NORMAL,-2444908528344618126\"\n," +
"            \"TOKENS\": \"<tokens>\"\n" +
"        }\n" +
"    ],\n" +
"    \"variant\": {\n" +
"        \"language\": null,\n" +
"        \"mediaType\": {\n" +
"            \"type\": \"application\",\n" +
"            \"subtype\": \"json\",\n" +
"            \"parameters\": {},\n" +
"            \"wildcardType\": false,\n" +
"            \"wildcardSubtype\": false\n" +
"        },\n" +
"        \"encoding\": null,\n" +
"        \"languageString\": null\n" +
"    },\n" +
"    \"annotations\": [],\n" +
"    \"mediaType\": {\n" +
"        \"type\": \"application\",\n" +
"        \"subtype\": \"json\",\n" +
"        \"parameters\": {},\n" +
"        \"wildcardType\": false,\n" +
"        \"wildcardSubtype\": false\n" +
"    },\n" +
"    \"language\": null,\n" +
"    \"encoding\": null\n" +
"}";
}
