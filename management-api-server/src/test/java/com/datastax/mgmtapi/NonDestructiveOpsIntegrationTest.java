/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import javax.net.ssl.SSLException;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.resources.models.CompactRequest;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import com.datastax.mgmtapi.resources.models.ScrubRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.jboss.resteasy.core.messagebody.WriterUtility;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.Uninterruptibles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Class for integration testing non-destructive actions. By non-destructive this means actions that do not leave a node
 * in an inoperable state (e.g. assassinate or decommission). The purpose of this is to speed up testing by starting the Cassandra
 * node once, running all tests, and then stopping rather than a start/stop during each test case.
 */
@RunWith(Parameterized.class)
public class NonDestructiveOpsIntegrationTest extends BaseDockerIntegrationTest
{
    private static final Logger logger = LoggerFactory.getLogger(NonDestructiveOpsIntegrationTest.class);


    public NonDestructiveOpsIntegrationTest(String version)
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

    @Test
    public void testSeedReload() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        String requestSuccessful = client.post(URI.create(BASE_PATH + "/ops/seeds/reload").toURL(), null)
                .thenApply(r -> {
                    if (r.status().code() == HttpStatus.SC_OK)
                    {
                        byte[] versionBytes = new byte[r.content().readableBytes()];
                        r.content().readBytes(versionBytes);

                        return new String(versionBytes);
                    }

                    return null;
                }).join();

        //Empty because getSeeds removes local node
        assertEquals("[]", requestSuccessful);
    }

    @Test
    public void testConsistencyCheck() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        Integer code  = client.get(URI.create(BASE_PATH + "/probes/cluster?consistency_level=ONE").toURL())
                .thenApply(r -> {
                    byte[] versionBytes = new byte[r.content().readableBytes()];
                    r.content().readBytes(versionBytes);
                    logger.info(new String(versionBytes));

                    return r.status().code();
                }).join();

        assertEquals(200, (int) code);

        code = client.get(URI.create(BASE_PATH + "/probes/cluster?consistency_level=QUORUM").toURL())
                .thenApply(r -> {
                    byte[] versionBytes = new byte[r.content().readableBytes()];
                    r.content().readBytes(versionBytes);
                    logger.info(new String(versionBytes));

                    return r.status().code();
                }).join();

        assertEquals(500, (int) code);

        code = client.get(URI.create(BASE_PATH + "/probes/cluster?consistency_level=QUORUM&rf_per_dc=1").toURL())
                .thenApply(r -> {
                    byte[] versionBytes = new byte[r.content().readableBytes()];
                    r.content().readBytes(versionBytes);
                    logger.info(new String(versionBytes));

                    return r.status().code();
                }).join();

        assertEquals(200, (int) code);
    }

    @Test
    public void testSetCompactionThroughput() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URI uri = new URIBuilder(BASE_PATH + "/ops/node/compaction")
                .addParameter("value", "5")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testSetLoggingLevel() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URI uri = new URIBuilder(BASE_PATH + "/ops/node/logging")
                .addParameter("target", "cql")
                .addParameter("rawLevel", "debug")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testTruncateWithHost() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URI uri = new URIBuilder(BASE_PATH + "/ops/node/hints/truncate")
                .addParameter("host", "127.0.0.1")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testTruncateWithoutHost() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

       NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URI uri = new URIBuilder(BASE_PATH + "/ops/node/hints/truncate")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testResetLocalSchema() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URI uri = new URIBuilder(BASE_PATH + "/ops/node/schema/reset")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testReloadLocalSchema() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URI uri = new URIBuilder(BASE_PATH + "/ops/node/schema/reload")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testGetReleaseVersion() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URI uri = new URIBuilder(BASE_PATH + "/metadata/versions/release")
                .build();
        String response = client.get(uri.toURL())
                .thenApply(r -> {
                    if (r.status().code() == HttpStatus.SC_OK)
                    {
                        byte[] versionBytes = new byte[r.content().readableBytes()];
                        r.content().readBytes(versionBytes);

                        return new String(versionBytes);
                    }

                    return null;
                }).join();
        assertNotNull(response);
        assertNotEquals("", response);
    }

    @Test
    public void testGetEndpoints() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URI uri = new URIBuilder(BASE_PATH + "/metadata/endpoints")
                .build();
        String response = client.get(uri.toURL())
                .thenApply(r -> {
                    if (r.status().code() == HttpStatus.SC_OK)
                    {
                        byte[] versionBytes = new byte[r.content().readableBytes()];
                        r.content().readBytes(versionBytes);

                        return new String(versionBytes);
                    }

                    return null;
                }).join();

        System.err.println(response);
        assertNotNull(response);
        assertNotEquals("", response);
    }

    @Test
    public void testCleanup() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "system_traces", Collections.singletonList("events"));
        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace/cleanup")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), keyspaceRequestAsJSON)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testRefresh() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace/refresh")
                .addParameter("keyspaceName", "system_traces")
                .addParameter("table", "events")
                .addParameter("resetLevels", "true")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testScrub() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        ScrubRequest scrubRequest = new ScrubRequest(true, true, true, true,
                2, "system_traces", Collections.singletonList("events"));
        String requestAsJSON = WriterUtility.asString(scrubRequest, MediaType.APPLICATION_JSON);
        URI uri = new URIBuilder(BASE_PATH + "/ops/tables/scrub")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), requestAsJSON)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testCompact() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        CompactRequest compactRequest = new CompactRequest(false, false, null,
                null, "system_traces", null, Collections.singletonList("events"));
        String requestAsJSON = WriterUtility.asString(compactRequest, MediaType.APPLICATION_JSON);
        URI uri = new URIBuilder(BASE_PATH + "/ops/tables/compact")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), requestAsJSON)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testGarbageCollect() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "system_traces", Collections.singletonList("events"));
        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        URI uri = new URIBuilder(BASE_PATH + "/ops/tables/garbagecollect")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), keyspaceRequestAsJSON)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testFlush() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, null, null);
        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        URI uri = new URIBuilder(BASE_PATH + "/ops/tables/flush")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), keyspaceRequestAsJSON)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testUpgradeSSTables() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "", null);
        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        URI uri = new URIBuilder(BASE_PATH + "/ops/tables/sstables/upgrade")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), keyspaceRequestAsJSON)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testGetStreamInfo() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URI uri = new URIBuilder(BASE_PATH + "/ops/node/streaminfo").build();
        String response = client.get(uri.toURL())
                .thenApply(r -> {
                    if (r.status().code() == HttpStatus.SC_OK)
                    {
                        byte[] result = new byte[r.content().readableBytes()];
                        r.content().readBytes(result);

                        return new String(result);
                    }

                    return null;
                }).join();
        assertNotNull(response);
        assertNotEquals("", response);
    }
}
