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
import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

@ParameterizedClass(name = "{index}: {0}", allowZeroInvocations = true)
@MethodSource("testVersions")
public class PortOverrideIT extends BaseDockerIntegrationTest {

  private static final String ALT_PORT = "9090";
  private static final String ALT_PATH = String.format("http://localhost:%s/api/v0", ALT_PORT);

  public PortOverrideIT(String version) throws IOException {
    super(version);
  }

  @Override
  protected ArrayList<String> getEnvironmentVars() {
    // override env variable to change listen port
    return Lists.newArrayList("MGMT_API_LISTEN_TCP_PORT=" + ALT_PORT);
  }

  @Test
  public void testListesnOnAltPort() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());

    // Nee to use an alternate port to build the client
    final URL baseUrl = new URL(ALT_PATH);
    final NettyHttpClient client = new NettyHttpClient(baseUrl);
    // Verify liveness
    boolean live =
        client
            .get(URI.create(ALT_PATH + "/probes/liveness").toURL())
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();

    assertTrue(live);
  }
}
