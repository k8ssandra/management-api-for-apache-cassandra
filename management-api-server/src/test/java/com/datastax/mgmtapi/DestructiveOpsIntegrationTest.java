package com.datastax.mgmtapi;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpIPCClient;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.bdp.util.SocketUtils;

import com.google.common.util.concurrent.Uninterruptibles;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class DestructiveOpsIntegrationTest
{
    private static final Logger logger = LoggerFactory.getLogger(NonDestructiveOpsIntegrationTest.class);
    private static String BASE_PATH = "http://localhost/api/v0";
    private static String MGMT_SOCK;
    private static Cli CLI;

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void startDSE() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        MGMT_SOCK = SocketUtils.makeValidUnixSocketFile(null, "management-destr-mgmt");
        new File(MGMT_SOCK).deleteOnExit();
        String dseSock = SocketUtils.makeValidUnixSocketFile(null, "management-destr-dse");
        new File(dseSock).deleteOnExit();

        String dseExe = System.getProperty("dse.bin") + "/dse";

        List<String> extraArgs = IntegrationTestUtils.getExtraArgs(DestructiveOpsIntegrationTest.class, "", temporaryFolder.getRoot());

        CLI = new Cli(Collections.singletonList("file://" + MGMT_SOCK), dseExe, dseSock, false, extraArgs);

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

        assertTrue(ready);
    }

    // NOTE: Not testing decommission due to running the integration test against only one node and getting
    // java.lang.UnsupportedOperationException: no other normal nodes in the ring; decommission would be pointless

    // NOTE: Not testing assassinate due to running the integration test against only one node and getting
    // Caused by: java.lang.RuntimeException: Endpoint still alive: /127.0.0.1 heartbeat changed while trying to assassinate it
    //	at org.apache.cassandra.gms.Gossiper.assassinateEndpoint(Gossiper.java:711)
    //	at com.datastax.bdp.server.system.RpcOperationsProvider.assassinate(RpcOperationsProvider.java:90)
    //	... 22 common frames omitted

    @Test
    public void testDrain() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        NettyHttpIPCClient client = new NettyHttpIPCClient(MGMT_SOCK);


        URI uri = new URIBuilder(BASE_PATH + "/ops/node/drain")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @After
    public void stopDSE() throws MalformedURLException, UnsupportedEncodingException
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
        finally
        {
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
