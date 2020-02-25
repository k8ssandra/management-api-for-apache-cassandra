/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import javax.net.ssl.SSLException;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpIPCClient;
import com.datastax.mgmtapi.resources.models.CompactRequest;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import com.datastax.mgmtapi.resources.models.ScrubRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.jboss.resteasy.core.messagebody.WriterUtility;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.util.SocketUtils;

import com.google.common.util.concurrent.Uninterruptibles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Class for integration testing non-destructive actions. By non-destructive this means actions that do not leave a node
 * in an inoperable state (e.g. assassinate or decommission). The purpose of this is to speed up testing by starting the DSE
 * node once, running all tests, and then stopping rather than a start/stop during each test case.
 */
public class NonDestructiveOpsIntegrationTest
{

    private static final Logger logger = LoggerFactory.getLogger(NonDestructiveOpsIntegrationTest.class);
    private static String BASE_PATH = "http://localhost/api/v0";
    private static String MGMT_SOCK;
    private static String DSE_SOCK;
    private static Cli CLI;

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void startDSE() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        MGMT_SOCK = SocketUtils.makeValidUnixSocketFile(null, "management-nd-ops-mgmt");
        new File(MGMT_SOCK).deleteOnExit();
        DSE_SOCK = SocketUtils.makeValidUnixSocketFile(null, "management-nd-ops-dse");
        new File(DSE_SOCK).deleteOnExit();

        List<String> extraArgs = IntegrationTestUtils.getExtraArgs(NonDestructiveOpsIntegrationTest.class, "", temporaryFolder.getRoot());

        CLI = new Cli(Collections.singletonList("file://" + MGMT_SOCK), IntegrationTestUtils.getCassandraHome(), DSE_SOCK, false, extraArgs);

        CLI.preflightChecks();
        Thread cliThread = new Thread(CLI);
        cliThread.start();

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

        // Verify liveness
        boolean live = client.get(URI.create(BASE_PATH + "/probes/liveness").toURL())
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

        assertTrue(live);

        boolean ready = false;

        // Startup
        boolean started = client.post(URI.create(BASE_PATH + "/lifecycle/start").toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_CREATED).join();

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

        logger.info("DSE ALIVE: {}", ready);
        assertTrue(ready);
    }

    @Test
    public void testSeedReload() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

        URI uri = new URIBuilder(BASE_PATH + "/ops/node/compaction")
                .addParameter("value", "5")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testCreateRole() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

        URI uri = new URIBuilder(BASE_PATH + "/ops/auth/role")
                .addParameter("username", "mgmtuser")
                .addParameter( "is_superuser", "true")
                .addParameter("can_login", "true")
                .addParameter("password", "inttest")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }


    @Test
    public void testSetLoggingLevel() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

       NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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
    public void testCleanup() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

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

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "", null);
        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        URI uri = new URIBuilder(BASE_PATH + "/ops/tables/sstables/upgrade")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), keyspaceRequestAsJSON)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }


    @AfterClass
    public static void stopDSE() throws MalformedURLException, UnsupportedEncodingException
    {
        try
        {
            NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);

            boolean stopped = client.post(URI.create(BASE_PATH + "/lifecycle/stop").toURL(), null)
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertTrue(stopped);

            int tries = 0;
            boolean ready = true;
            while (tries++ < 20)
            {
                ready = client.get(URI.create(BASE_PATH + "/probes/readiness").toURL())
                        .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

                if (!ready)
                    break;

                Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
            }

            assertFalse(ready);
        }
        catch (SSLException e)
        {
            throw new RuntimeException(e);
        }
        finally {
            try
            {
                if (CLI != null)
                {
                    CLI.stop();
                }
            }
            catch (Exception e)
            {
                logger.error("Unable to stop cli", e);
            }
        }
    }
}
