/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v2;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.common.BaseResources;
import com.datastax.mgmtapi.resources.models.Table;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

@Path("/api/v2/ops/tables")
public class TableOpsResources extends BaseResources {

  public TableOpsResources(ManagementApplication application) {
    super(application);
  }

  @GET
  @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
  @ApiResponse(
      responseCode = "200",
      description = "Table list",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              array = @ArraySchema(schema = @Schema(implementation = Table.class))))
  @ApiResponse(
      responseCode = "400",
      description = "Keyspace name not provided",
      content =
          @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = String.class),
              examples =
                  @ExampleObject(
                      value = "List tables failed. Non-empty 'keyspaceName' must be provided")))
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "List the table names in the given keyspace", operationId = "listTables")
  public Response list(
      @Parameter(required = true) @QueryParam(value = "keyspaceName") String keyspaceName) {
    if (StringUtils.isBlank(keyspaceName)) {
      return Response.status(HttpStatus.SC_BAD_REQUEST)
          .entity("List tables failed. Non-empty 'keyspaceName' must be provided")
          .build();
    }
    return handle(
        () -> {
          ResultSet result =
              app.cqlService.executePreparedStatement(
                  app.dbUnixSocketFile, "CALL NodeOps.getTables(?)", keyspaceName);
          List<Table> tables = Lists.newArrayList();
          for (Row row : result) {
            tables.add(
                new Table(
                    row.getString("name"), row.getMap("compaction", String.class, String.class)));
          }
          return Response.ok(tables, MediaType.APPLICATION_JSON).build();
        });
  }
}
