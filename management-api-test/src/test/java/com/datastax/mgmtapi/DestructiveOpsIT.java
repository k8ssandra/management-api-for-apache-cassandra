/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ParameterizedClass(name = "{index}: {0}", allowZeroInvocations = true)
@MethodSource("testVersions")
public class DestructiveOpsIT extends BaseDockerIsolatedIntegrationTest {
  private static final Logger logger = LoggerFactory.getLogger(NonDestructiveOpsIT.class);

  public DestructiveOpsIT(String version) throws IOException {
    super(version);
  }

  /**
   * NOTE: Not testing decommission due to running the integration test against only one node and
   * getting java.lang.UnsupportedOperationException: no other normal nodes in the ring;
   * decommission would be pointless
   *
   * <p>NOTE: Not testing assassinate due to running the integration test against only one node and
   * hits a RTE
   */
  @Test
  public void testDrain() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = getClient();

    URI uri = new URIBuilder(BASE_PATH + "/ops/node/drain").build();
    boolean requestSuccessful =
        client.post(uri.toURL(), null).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
    assertTrue(requestSuccessful);
  }
}
