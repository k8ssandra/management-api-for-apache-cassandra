/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v2;

import static com.datastax.mgmtapi.NonDestructiveOpsIT.ensureStarted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import com.datastax.mgmtapi.BaseDockerIntegrationTest;
import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.resources.v2.models.TokenRangeToEndpointResponse;
import java.io.IOException;
import java.net.URI;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonDestructiveOpsV2IT extends BaseDockerIntegrationTest {

  public NonDestructiveOpsV2IT(String version) throws IOException {
    super(version);
  }

  @Test
  public void testGetTokenRangeToEndpointMap() throws Exception {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);
    final URIBuilder uriBuilder = new URIBuilder(BASE_PATH_V2 + "/tokens/rangetoendpoint");
    // test keyspace not found
    URI uri = uriBuilder.setParameter("keyspaceName", "notfoundkeyspace").build();
    Pair<Integer, String> response =
        client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_NOT_FOUND);
    // test keyspace exists
    uri = uriBuilder.setParameter("keyspaceName", "system_schema").build();
    response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);
    String mappingString = response.getRight();
    assertThat(mappingString).isNotNull().isNotEmpty();
    TokenRangeToEndpointResponse mapping =
        JSON_MAPPER.readValue(mappingString, TokenRangeToEndpointResponse.class);
    assertThat(mapping.tokenRangeToEndpoints).isNotNull().isNotEmpty().hasSize(getNumTokenRanges());
  }
}
