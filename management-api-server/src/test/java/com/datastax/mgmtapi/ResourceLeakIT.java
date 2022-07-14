/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static com.datastax.mgmtapi.BaseDockerIntegrationTest.BASE_PATH;
import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ResourceLeakIT extends BaseDockerIsolatedIntegrationTest {

  public ResourceLeakIT(String version) throws IOException {
    super(version);
  }

  @Test
  public void testLifecycle() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());

    try {
      NettyHttpClient client = getClient();

      //Verify liveness
      boolean live = client.get(URI.create(BASE_PATH + "/probes/liveness").toURL())
          .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

      assertTrue(live);
      
      //Verify readiness fails
      boolean ready = false;
      int tries = 0;
      while (tries++ < 2000) {
        ready = client.get(URI.create(BASE_PATH + "/probes/readiness").toURL())
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

        if (ready) {
          break;
        }
        
        Uninterruptibles.sleepUninterruptibly(50, TimeUnit.MILLISECONDS);
      }
    } finally {

    }
  }
}
