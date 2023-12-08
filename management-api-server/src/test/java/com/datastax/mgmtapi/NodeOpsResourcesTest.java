/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.mgmtapi.resources.K8OperatorResources;
import com.datastax.mgmtapi.resources.KeyspaceOpsResources;
import com.datastax.mgmtapi.resources.MetadataResources;
import com.datastax.mgmtapi.resources.NodeOpsResources;
import com.datastax.mgmtapi.resources.TableOpsResources;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import org.apache.http.HttpStatus;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Assert;
import org.junit.Test;

public class NodeOpsResourcesTest {

  static String ROOT_PATH = "/api/v0/ops/node";

  static class Context {

    Dispatcher dispatcher;
    CqlService cqlService;

    MockHttpResponse invoke(HttpRequest request) {
      MockHttpResponse response = new MockHttpResponse();
      dispatcher.invoke(request, response);
      return response;
    }
  }

  private static Dispatcher createDispatcher() {
    return MockDispatcherFactory.createDispatcher();
  }

  static NodeOpsResourcesTest.Context setup() {
    NodeOpsResourcesTest.Context context = new NodeOpsResourcesTest.Context();
    context.dispatcher = createDispatcher();
    context.cqlService = mock(CqlService.class);

    ManagementApplication app =
        new ManagementApplication(null, null, null, context.cqlService, null);

    context.dispatcher.getRegistry().addSingletonResource(new K8OperatorResources(app));
    context.dispatcher.getRegistry().addSingletonResource(new KeyspaceOpsResources(app));
    context
        .dispatcher
        .getRegistry()
        .addSingletonResource(new com.datastax.mgmtapi.resources.v1.KeyspaceOpsResources(app));
    context.dispatcher.getRegistry().addSingletonResource(new MetadataResources(app));
    context.dispatcher.getRegistry().addSingletonResource(new NodeOpsResources(app));
    context
        .dispatcher
        .getRegistry()
        .addSingletonResource(new com.datastax.mgmtapi.resources.v1.NodeOpsResources(app));
    context.dispatcher.getRegistry().addSingletonResource(new TableOpsResources(app));
    context
        .dispatcher
        .getRegistry()
        .addSingletonResource(new com.datastax.mgmtapi.resources.v1.TableOpsResources(app));

    return context;
  }

  @Test
  public void testRebuildSearchIndex() throws Exception {

    NodeOpsResourcesTest.Context context = setup();

    Row mockSystemLocalRow = mock(Row.class);
    ResultSet mockSystemLocalResultSet = mock(ResultSet.class);
    ColumnDefinitions mockColumnDefinitions = mock(ColumnDefinitions.class);

    ResultSet mockRebuildResultSet = mock(ResultSet.class);

    // mock the interaction with system.local
    when(mockSystemLocalResultSet.one()).thenReturn(mockSystemLocalRow);
    when(mockSystemLocalRow.getColumnDefinitions()).thenReturn(mockColumnDefinitions);
    when(mockColumnDefinitions.contains(anyString())).thenReturn(Boolean.TRUE);
    when(context.cqlService.executeCql(any(), startsWith("SELECT * FROM system.local;")))
        .thenReturn(mockSystemLocalResultSet);

    // mock the actual rebuild call
    when(context.cqlService.executeCql(any(), startsWith("REBUILD SEARCH INDEX")))
        .thenReturn(mockRebuildResultSet);

    MockHttpRequest request =
        MockHttpRequest.post(ROOT_PATH + "/ops/search/rebuildIndex?keyspace=ks&table=t");
    MockHttpResponse response = context.invoke(request);

    Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
  }

  @Test
  public void testRebuildSearchIndexNonDse() throws Exception {

    NodeOpsResourcesTest.Context context = setup();

    Row mockSystemLocalRow = mock(Row.class);
    ResultSet mockSystemLocalResultSet = mock(ResultSet.class);
    ColumnDefinitions mockColumnDefinitions = mock(ColumnDefinitions.class);

    ResultSet mockRebuildResultSet = mock(ResultSet.class);

    // mock the interaction with system.local
    when(mockSystemLocalResultSet.one()).thenReturn(mockSystemLocalRow);
    when(mockSystemLocalRow.getColumnDefinitions()).thenReturn(mockColumnDefinitions);
    // here we indicate we're not dealing with DSE
    when(mockColumnDefinitions.contains(anyString())).thenReturn(Boolean.FALSE);
    when(context.cqlService.executeCql(any(), startsWith("SELECT * FROM system.local;")))
        .thenReturn(mockSystemLocalResultSet);

    // mock the actual rebuild call
    when(context.cqlService.executeCql(any(), startsWith("REBUILD SEARCH INDEX")))
        .thenReturn(mockRebuildResultSet);

    MockHttpRequest request =
        MockHttpRequest.post(ROOT_PATH + "/ops/search/rebuildIndex?keyspace=ks&table=t");
    MockHttpResponse response = context.invoke(request);

    Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
  }

  @Test
  public void testRebuildNonExistentSearchIndex() throws Exception {

    NodeOpsResourcesTest.Context context = setup();

    Row mockSystemLocalRow = mock(Row.class);
    ResultSet mockSystemLocalResultSet = mock(ResultSet.class);
    ColumnDefinitions mockColumnDefinitions = mock(ColumnDefinitions.class);

    // mock the interaction with system.local
    when(mockSystemLocalResultSet.one()).thenReturn(mockSystemLocalRow);
    when(mockSystemLocalRow.getColumnDefinitions()).thenReturn(mockColumnDefinitions);
    when(mockColumnDefinitions.contains(anyString())).thenReturn(Boolean.TRUE);
    when(context.cqlService.executeCql(any(), startsWith("SELECT * FROM system.local;")))
        .thenReturn(mockSystemLocalResultSet);

    // mock the actual rebuild call
    // the driver throws an invalid query exception if we do this on a table w/o an index
    when(context.cqlService.executeCql(any(), startsWith("REBUILD SEARCH INDEX")))
        .thenThrow(InvalidQueryException.class);

    MockHttpRequest request =
        MockHttpRequest.post(ROOT_PATH + "/ops/search/rebuildIndex?keyspace=ks&table=t");
    MockHttpResponse response = context.invoke(request);

    Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
  }

  @Test
  public void testRebuildSearchIndexInternalError() throws Exception {

    NodeOpsResourcesTest.Context context = setup();

    ResultSet mockSystemLocalResultSet = mock(ResultSet.class);

    // mock the interaction with system.local
    when(mockSystemLocalResultSet.one()).thenReturn(null);
    when(context.cqlService.executeCql(any(), startsWith("SELECT * FROM system.local;")))
        .thenReturn(mockSystemLocalResultSet);

    // mock the actual rebuild call
    when(context.cqlService.executeCql(any(), startsWith("REBUILD SEARCH INDEX")))
        .thenThrow(InvalidQueryException.class);

    MockHttpRequest request =
        MockHttpRequest.post(ROOT_PATH + "/ops/search/rebuildIndex?keyspace=ks&table=t");
    MockHttpResponse response = context.invoke(request);

    Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
  }
}
