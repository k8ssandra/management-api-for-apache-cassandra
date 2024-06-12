/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.mgmtapi.resources.K8OperatorResources;
import com.datastax.mgmtapi.resources.KeyspaceOpsResources;
import com.datastax.mgmtapi.resources.MetadataResources;
import com.datastax.mgmtapi.resources.NodeOpsResources;
import com.datastax.mgmtapi.resources.TableOpsResources;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import org.apache.http.ConnectionClosedException;
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

    mockGetReleaseVersion(context, "4.0.0.6839");

    ResultSet mockRebuildResultSet = mock(ResultSet.class);

    // mock the actual rebuild call
    when(context.cqlService.executeCql(any(), startsWith("REBUILD SEARCH INDEX")))
        .thenReturn(mockRebuildResultSet);

    makeRequestWithExpectedResponse(context, HttpStatus.SC_OK);
  }

  @Test
  public void testRebuildSearchIndexDse69() throws Exception {

    NodeOpsResourcesTest.Context context = setup();

    mockGetReleaseVersion(context, "4.0.0.690-early-preview");

    ResultSet mockRebuildResultSet = mock(ResultSet.class);

    // mock the actual rebuild call
    when(context.cqlService.executeCql(any(), startsWith("REBUILD SEARCH INDEX")))
        .thenReturn(mockRebuildResultSet);

    makeRequestWithExpectedResponse(context, HttpStatus.SC_OK);
  }

  @Test
  public void testRebuildSearchIndexNonDse() throws Exception {

    NodeOpsResourcesTest.Context context = setup();

    mockGetReleaseVersion(context, "4.0.0");

    ResultSet mockRebuildResultSet = mock(ResultSet.class);

    // mock the actual rebuild call
    when(context.cqlService.executeCql(any(), startsWith("REBUILD SEARCH INDEX")))
        .thenReturn(mockRebuildResultSet);

    makeRequestWithExpectedResponseAndBody(
        context, HttpStatus.SC_BAD_REQUEST, "Rebuilding Search Index is only supported on DSE");
  }

  @Test
  public void testRebuildNonExistentSearchIndex() throws Exception {

    NodeOpsResourcesTest.Context context = setup();

    mockGetReleaseVersion(context, "4.0.0.6839");

    // mock the actual rebuild call
    // the driver throws an invalid query exception if we do this on a table w/o an index
    when(context.cqlService.executeCql(any(), startsWith("REBUILD SEARCH INDEX")))
        .thenThrow(InvalidQueryException.class);

    makeRequestWithExpectedResponse(context, HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void testRebuildSearchIndexInternalError() throws Exception {

    NodeOpsResourcesTest.Context context = setup();

    mockGetReleaseVersion(context, null);

    // mock the actual rebuild call
    when(context.cqlService.executeCql(any(), startsWith("REBUILD SEARCH INDEX")))
        .thenThrow(InvalidQueryException.class);

    makeRequestWithExpectedResponse(context, HttpStatus.SC_INTERNAL_SERVER_ERROR);
  }

  private void mockGetReleaseVersion(NodeOpsResourcesTest.Context context, String version)
      throws ConnectionClosedException {
    ResultSet mockVersionResultSet = mock(ResultSet.class);
    Row mockRow = mock(Row.class);

    when(mockRow.getString(0)).thenReturn(version);
    when(mockVersionResultSet.one()).thenReturn(mockRow);
    when(context.cqlService.executeCql(any(), startsWith("CALL NodeOps.getReleaseVersion()")))
        .thenReturn(mockVersionResultSet);
  }

  private void makeRequestWithExpectedResponse(
      NodeOpsResourcesTest.Context context, int expectedStatus) throws URISyntaxException {
    MockHttpRequest request =
        MockHttpRequest.post(ROOT_PATH + "/search/rebuildIndex?keyspace=ks&table=t");
    MockHttpResponse response = context.invoke(request);

    Assert.assertEquals(expectedStatus, response.getStatus());
  }

  private void makeRequestWithExpectedResponseAndBody(
      NodeOpsResourcesTest.Context context, int expectedStatus, String body)
      throws URISyntaxException, UnsupportedEncodingException {
    MockHttpRequest request =
        MockHttpRequest.post(ROOT_PATH + "/search/rebuildIndex?keyspace=ks&table=t");
    MockHttpResponse response = context.invoke(request);

    Assert.assertEquals(expectedStatus, response.getStatus());
    Assert.assertEquals("Response body did not match", body, response.getContentAsString());
  }
}
