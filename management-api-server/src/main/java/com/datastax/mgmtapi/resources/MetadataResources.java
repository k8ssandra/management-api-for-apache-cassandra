/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.EndpointStates;
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
public class MetadataResources extends BaseResources {
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
              schema = @Schema(implementation = EndpointStates.class)))
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
}
