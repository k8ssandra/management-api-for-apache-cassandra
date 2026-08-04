/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.apache.http.HttpStatus;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.Dispatcher;
import org.junit.Before;
import org.junit.Test;

public class AuthResourcesV1Test {
  private static final String PATH = "/api/v1/ops/auth/identity_to_role";
  private static final String IDENTITY =
      "spiffe://sidecar.prod.example.com/user/u_12345/credential/credential-id";
  private static final String ROLE = "schema_reader";

  private Dispatcher dispatcher;
  private CqlService cqlService;

  @Before
  public void setUp() {
    K8OperatorResourcesTest.Context context = K8OperatorResourcesTest.setup();
    dispatcher = context.dispatcher;
    cqlService = context.cqlService;
  }

  @Test
  public void createsIdentityToRoleMapping() throws Exception {
    mockReleaseVersion("6.0.0");
    MockHttpRequest request =
        MockHttpRequest.post(PATH)
            .contentType("application/json")
            .content(("{\"identity\":\"" + IDENTITY + "\",\"role\":\"" + ROLE + "\"}").getBytes());

    MockHttpResponse response = invoke(request);

    assertEquals(HttpStatus.SC_OK, response.getStatus());
    assertEquals("OK", response.getContentAsString());
    verify(cqlService)
        .executePreparedStatement(
            any(), eq("CALL NodeOps.addIdentityToRole(?, ?)"), eq(IDENTITY), eq(ROLE));
  }

  @Test
  public void deletesIdentityToRoleMapping() throws Exception {
    mockReleaseVersion("5.0.7.0-9bbf7793dc0b");
    MockHttpRequest request =
        MockHttpRequest.delete(PATH)
            .contentType("application/json")
            .content(("{\"identity\":\"" + IDENTITY + "\"}").getBytes());

    MockHttpResponse response = invoke(request);

    assertEquals(HttpStatus.SC_OK, response.getStatus());
    assertEquals("OK", response.getContentAsString());
    verify(cqlService)
        .executePreparedStatement(any(), eq("CALL NodeOps.deleteIdentityToRole(?)"), eq(IDENTITY));
  }

  @Test
  public void rejectsMissingPostFields() throws Exception {
    MockHttpRequest request =
        MockHttpRequest.post(PATH)
            .contentType("application/json")
            .content(("{\"identity\":\"" + IDENTITY + "\"}").getBytes());

    MockHttpResponse response = invoke(request);

    assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Role and identity are required", response.getContentAsString());
  }

  @Test
  public void rejectsMissingPostBody() throws Exception {
    MockHttpRequest request = MockHttpRequest.post(PATH).contentType("application/json");

    MockHttpResponse response = invoke(request);

    assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Request body is empty", response.getContentAsString());
  }

  @Test
  public void rejectsMissingDeleteIdentity() throws Exception {
    MockHttpRequest request =
        MockHttpRequest.delete(PATH).contentType("application/json").content("{}".getBytes());

    MockHttpResponse response = invoke(request);

    assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Identity is empty", response.getContentAsString());
  }

  @Test
  public void rejectsMissingDeleteBody() throws Exception {
    MockHttpRequest request = MockHttpRequest.delete(PATH).contentType("application/json");

    MockHttpResponse response = invoke(request);

    assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Request body is empty", response.getContentAsString());
  }

  @Test
  public void rejectsUnsupportedCassandraVersion() throws Exception {
    mockReleaseVersion("4.1.10");
    MockHttpRequest request =
        MockHttpRequest.delete(PATH)
            .contentType("application/json")
            .content(("{\"identity\":\"" + IDENTITY + "\"}").getBytes());

    MockHttpResponse response = invoke(request);

    assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    assertTrue(response.getContentAsString().contains("Cassandra 5.0 or newer"));
  }

  private void mockReleaseVersion(String releaseVersion) throws Exception {
    ResultSet resultSet = mock(ResultSet.class);
    Row row = mock(Row.class);
    when(cqlService.executeCql(any(), eq("CALL NodeOps.getReleaseVersion()")))
        .thenReturn(resultSet);
    when(resultSet.one()).thenReturn(row);
    when(row.getString(0)).thenReturn(releaseVersion);
  }

  private MockHttpResponse invoke(MockHttpRequest request) {
    MockHttpResponse response = new MockHttpResponse();
    dispatcher.invoke(request, response);
    return response;
  }
}
