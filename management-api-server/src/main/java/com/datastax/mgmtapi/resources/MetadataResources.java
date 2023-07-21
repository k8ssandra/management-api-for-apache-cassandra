/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResource;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.FeatureSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/api/v0/metadata")
public class MetadataResources extends BaseResource {
  private static final String CASSANDRA_VERSION_CQL_STRING = "CALL NodeOps.getReleaseVersion()";

  public MetadataResources(ManagementApplication application) {
    super(application);
  }

  @GET
  @Path("/versions/release")
  @Operation(summary = "Returns the Cassandra release version", operationId = "getReleaseVersion")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cassandra version'",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "4.0.1")))
  public Response getReleaseVersion() {
    return executeWithStringResponse(CASSANDRA_VERSION_CQL_STRING);
  }

  @GET
  @Path("/endpoints")
  @Operation(
      summary = "Returns this nodes view of the endpoint states of nodes",
      operationId = "getEndpointStates")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(
      responseCode = "200",
      description = "Endpoint states'",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = ENDPOINTS_RESPONSE_EXAMPLE)))
  public Response getEndpointStates() {
    return executeWithJSONResponse("CALL NodeOps.getEndpointStates()");
  }

  @GET
  @Path("/localdc")
  @Operation(
      summary = "Returns the DataCenter the local node belongs to",
      operationId = "getLocalDataCenter")
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Local datacenter'",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "datacenter1")))
  public Response getLocalDataCenter() {
    return executeWithStringResponse("CALL NodeOps.getLocalDataCenter()");
  }

  @GET
  @Path("/versions/features")
  @Operation(summary = "Returns the management-api featureSet", operationId = "getFeatureSet")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(
      responseCode = "200",
      description = "Local datacenter'",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = FeatureSet.class)))
  public Response getFeatureSet() {
    return handle(
        () -> {
          String cassandraVersion =
              ResponseTools.getSingleRowStringResponse(
                  app.dbUnixSocketFile, app.cqlService, CASSANDRA_VERSION_CQL_STRING);
          // TODO management-api-release-version is not included in the release packages
          FeatureSet featureSet = new FeatureSet(cassandraVersion, "");
          return Response.ok(featureSet).build();
        });
  }

  private static final String ENDPOINTS_RESPONSE_EXAMPLE =
      "{\n"
          + "    \"entity\": [\n"
          + "        {\n"
          + "            \"DC\": \"datacenter1\",\n"
          + "            \"ENDPOINT_IP\": \"172.17.0.2\",\n"
          + "            \"HOST_ID\": \"5d2318b0-cfec-4697-8ed2-014b5f434ae8\",\n"
          + "            \"IS_ALIVE\": \"true\",\n"
          + "            \"LOAD\": \"135527.0\",\n"
          + "            \"NATIVE_ADDRESS_AND_PORT\": \"172.17.0.2:9042\",\n"
          + "            \"NET_VERSION\": \"12\",\n"
          + "            \"PARTITIONER\": \"org.apache.cassandra.dht.Murmur3Partitioner\",\n"
          + "            \"RACK\": \"rack1\",\n"
          + "            \"RELEASE_VERSION\": \"4.0.1\",\n"
          + "            \"RPC_ADDRESS\": \"172.17.0.2\",\n"
          + "            \"RPC_READY\": \"true\",\n"
          + "            \"SCHEMA\": \"2207c2a9-f598-3971-986b-2926e09e239d\",\n"
          + "            \"SSTABLE_VERSIONS\": \"big-nb\",\n"
          + "            \"STATUS\": \"NORMAL,-2444908528344618126\",\n"
          + "            \"STATUS_WITH_PORT\": \"NORMAL,-2444908528344618126\"\n,"
          + "            \"TOKENS\": \"-2088271209278749360,-3055635452357284858,-4299155458037876832,-498266656764850722,-5378528265485478489,-6114443958196372130,-7190199839421951670,-8007871772034302464,-9025723176776729480,1120756638932192574,2098902091448306650,3877536392271893778,4973578292506832067,6064875403199044326,6909252050690206084,8349520280592789322\"\n"
          + "        }\n"
          + "    ],\n"
          + "    \"variant\": {\n"
          + "        \"language\": null,\n"
          + "        \"mediaType\": {\n"
          + "            \"type\": \"application\",\n"
          + "            \"subtype\": \"json\",\n"
          + "            \"parameters\": {},\n"
          + "            \"wildcardType\": false,\n"
          + "            \"wildcardSubtype\": false\n"
          + "        },\n"
          + "        \"encoding\": null,\n"
          + "        \"languageString\": null\n"
          + "    },\n"
          + "    \"annotations\": [],\n"
          + "    \"mediaType\": {\n"
          + "        \"type\": \"application\",\n"
          + "        \"subtype\": \"json\",\n"
          + "        \"parameters\": {},\n"
          + "        \"wildcardType\": false,\n"
          + "        \"wildcardSubtype\": false\n"
          + "    },\n"
          + "    \"language\": null,\n"
          + "    \"encoding\": null\n"
          + "}";
}
