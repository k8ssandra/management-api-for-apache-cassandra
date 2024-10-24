/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v2;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.v2.models.TokenRangeToEndpointResponse;
import com.datastax.mgmtapi.resources.v2.models.TokenRangeToEndpoints;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Path("/api/v2/tokens")
public class TokenResourcesV2 extends BaseResources {

  public TokenResourcesV2(ManagementApplication application) {
    super(application);
  }

  @GET
  @Path("/rangetoendpoint")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Retrieve a mapping of Token ranges to endpoints",
      operationId = "getRangeToEndpointMapV2")
  @ApiResponse(
      responseCode = "200",
      description = "Token range retrieval was successful",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = TokenRangeToEndpointResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Keyspace not found",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "keyspace not found")))
  public Response getRangeToEndpointMap(@QueryParam(value = "keyspaceName") String keyspaceName) {
    return handle(
        () -> {
          if (keyspaceName != null && !keyspaceExists(keyspaceName)) {
            return Response.status(Response.Status.NOT_FOUND).entity("keyspace not found").build();
          }

          Map<List<String>, List<String>> map =
              (Map<List<String>, List<String>>)
                  ResponseTools.getSingleRowResponse(
                      app.dbUnixSocketFile,
                      app.cqlService,
                      "CALL NodeOps.getRangeToEndpointMap(?)",
                      keyspaceName);
          return Response.ok(convert(map)).build();
        });
  }

  private TokenRangeToEndpointResponse convert(Map<List<String>, List<String>> map) {
    List<TokenRangeToEndpoints> rangesToEndpoints = new ArrayList<>(map.size());
    map.entrySet()
        .forEach(
            (Map.Entry<List<String>, List<String>> e) -> {
              rangesToEndpoints.add(
                  new TokenRangeToEndpoints(convertRanges(e.getKey()), e.getValue()));
            });
    return new TokenRangeToEndpointResponse(rangesToEndpoints);
  }

  private List<Long> convertRanges(List<String> range) {
    // each Range should be exactly 2 strings: start, end
    assert range.size() == 2;
    List<Long> tokenRange = Arrays.asList(Long.valueOf(range.get(0)), Long.parseLong(range.get(1)));
    return tokenRange;
  }
}
