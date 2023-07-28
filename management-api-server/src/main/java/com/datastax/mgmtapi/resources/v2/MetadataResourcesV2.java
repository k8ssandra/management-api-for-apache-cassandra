/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v2;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.EndpointStatesResponse;
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
import org.apache.http.ConnectionClosedException;

@Path("/api/v2/metadata")
public class MetadataResourcesV2 extends BaseResources {

  public MetadataResourcesV2(ManagementApplication application) {
    super(application);
  }

  @GET
  @Path("/endpoints")
  @Operation(
      summary = "Returns this nodes view of the endpoint states of nodes",
      operationId = "getEndpointStates")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(
      responseCode = "200",
      description = "Endpoint states",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = EndpointStatesResponse.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Error reading endpoint states",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "error message")))
  public Response getEndpointStates() {
    try {
      Object obj =
          ResponseTools.getSingleRowResponse(
              app.dbUnixSocketFile, app.cqlService, "CALL NodeOps.getEndpointStates()");
      // convert the result into a JSON Object
      final EndpointStatesResponse responseObj = new EndpointStatesResponse(obj);
      return Response.ok(responseObj).build();
    } catch (ConnectionClosedException ce) {
      ce.printStackTrace();
    }
    return Response.serverError().entity("Error rerading endpoint states").build();
  }
}
