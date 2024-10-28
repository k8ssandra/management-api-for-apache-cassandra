/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v1;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Path("/api/v1/ops/keyspace")
public class KeyspaceOpsResources extends BaseResources {

  public KeyspaceOpsResources(ManagementApplication application) {
    super(application);
  }

  @POST
  @Path("/cleanup")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  @ApiResponse(
      responseCode = "200",
      description = "Cleanup not needed for tables 'system' or 'system_schema'",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "OK")))
  @ApiResponse(
      responseCode = "202",
      description = "Job ID for keyspace cleanup process",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples = @ExampleObject(value = "d69d1d95-9348-4460-95d2-ae342870fade")))
  @Operation(
      summary =
          "Triggers the immediate cleanup of keys no longer belonging to a node. By default, clean all keyspaces. This operation is asynchronous and returns immediately",
      operationId = "cleanup_v1")
  public Response cleanup(KeyspaceRequest keyspaceRequest) {
    return handle(
        () -> {
          List<String> tables = keyspaceRequest.tables;
          if (CollectionUtils.isEmpty(tables)) {
            tables = new ArrayList<>();
          }

          String keyspaceName = keyspaceRequest.keyspaceName;
          if (StringUtils.isBlank(keyspaceName)) {
            keyspaceName = "NON_LOCAL_STRATEGY";
          }

          if (StringUtils.equalsAnyIgnoreCase(keyspaceName, "system", "system_schema")) {
            return Response.ok("OK").build();
          }

          return Response.accepted(
                  ResponseTools.getSingleRowStringResponse(
                      app.dbUnixSocketFile,
                      app.cqlService,
                      "CALL NodeOps.forceKeyspaceCleanup(?, ?, ?, ?)",
                      keyspaceRequest.jobs,
                      keyspaceName,
                      tables,
                      true))
              .build();
        });
  }
}
