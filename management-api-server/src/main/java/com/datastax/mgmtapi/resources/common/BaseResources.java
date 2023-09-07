/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.common;

import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.oss.driver.api.core.NoNodeAvailableException;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import java.util.concurrent.Callable;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpStatus;

public abstract class BaseResources {

  protected final ManagementApplication app;

  protected BaseResources(ManagementApplication application) {
    this.app = application;
  }

  /**
   * Executes a CQL query with the expectation that there will be a single row returned with type
   * String
   *
   * @param query CQL query to execute
   * @return Returns a Response with status code 200 and body of query response on success and
   *     status code 500 on failure
   */
  protected Response executeWithStringResponse(String query) {
    return handle(
        () ->
            Response.ok(
                    ResponseTools.getSingleRowStringResponse(
                        app.dbUnixSocketFile, app.cqlService, query))
                .build());
  }

  /**
   * Executes a CQL query with the expectation that there will be a single row returned with type
   * String
   *
   * @param query CQL query to execute
   * @return Returns a Response with status code 200 and body of query response on success and
   *     status code 500 on failure
   */
  protected Response executeWithJSONResponse(String query) {
    return handle(
        () ->
            Response.ok(
                    Entity.json(
                        ResponseTools.getSingleRowResponse(
                            app.dbUnixSocketFile, app.cqlService, query)))
                .build());
  }

  protected Response handle(Callable<Response> action) {
    try {
      return action.call();
    } catch (NoNodeAvailableException | ConnectionClosedException e) {
      return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
          .entity("Internal connection to Cassandra closed")
          .build();
    } catch (Throwable t) {
      t.printStackTrace();
      return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
          .entity(t.getLocalizedMessage())
          .build();
    }
  }

  protected boolean keyspaceExists(String keyspaceName) {
    if (keyspaceName != null) {
      try {
        ResultSet result =
            app.cqlService.executePreparedStatement(
                app.dbUnixSocketFile, "CALL NodeOps.getKeyspaces()");
        Row row = result.one();
        if (row != null) {
          return row.getList(0, String.class).contains(keyspaceName);
        }
      } catch (Exception e) {
        // cxonnection issue or some other problem. Eat it and return false.
      }
    }
    return false;
  }
}
