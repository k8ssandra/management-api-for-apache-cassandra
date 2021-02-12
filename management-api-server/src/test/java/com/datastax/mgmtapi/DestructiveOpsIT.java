/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;


import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.google.common.util.concurrent.Uninterruptibles;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class DestructiveOpsIT extends BaseDockerIntegrationTest
{
    private static final Logger logger = LoggerFactory.getLogger(NonDestructiveOpsIT.class);

    public DestructiveOpsIT(String version) throws IOException
    {
        super(version);
    }

    public static void ensureStarted() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        // Verify liveness
        boolean live = client.get(URI.create(BASE_PATH + "/probes/liveness").toURL())
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

        assertTrue(live);

        boolean ready = false;

        // Startup
        boolean started = client.post(URI.create(BASE_PATH + "/lifecycle/start").toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_CREATED || r.status().code() == HttpStatus.SC_ACCEPTED ).join();

        assertTrue(started);

        int tries = 0;
        while (tries++ < 10)
        {
            ready = client.get(URI.create(BASE_PATH + "/probes/readiness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            if (ready)
                break;

            Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
        }

        logger.info("CASSANDRA ALIVE: {}", ready);
        assertTrue(ready);
    }


    // NOTE: Not testing decommission due to running the integration test against only one node and getting
    // java.lang.UnsupportedOperationException: no other normal nodes in the ring; decommission would be pointless

    // NOTE: Not testing assassinate due to running the integration test against only one node and hits a RTE
    @Test
    public void testDrain() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = getClient();

        URI uri = new URIBuilder(BASE_PATH + "/ops/node/drain").build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

}
