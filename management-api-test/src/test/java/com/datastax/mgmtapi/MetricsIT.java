/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class MetricsIT extends BaseDockerIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(MetricsIT.class);
  private static final String BASE_METRICS_PATH = "http://localhost:9000/metrics";
  private static final URL BASE_METRICS_URL;

  static {
    try {
      BASE_METRICS_URL = new URL(BASE_METRICS_PATH);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public MetricsIT(String version) throws IOException {
    super(version);
  }

  @Test
  public void testGetJvmMetrics() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_METRICS_URL);
    Integer code =
        client
            .get(URI.create(BASE_METRICS_PATH + "?name=jvm").toURL())
            .thenApply(
                r -> {
                  byte[] metricsBytes = new byte[r.content().readableBytes()];
                  r.content().readBytes(metricsBytes);
                  logger.info(new String(metricsBytes));

                  return r.status().code();
                })
            .join();

    assertEquals(200, (int) code);
  }
}
