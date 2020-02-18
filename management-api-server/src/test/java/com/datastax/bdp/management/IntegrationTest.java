/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.bdp.management;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.datastax.bdp.management.helpers.IntegrationTestUtils;
import com.datastax.bdp.management.helpers.NettyHttpIPCClient;
import com.datastax.bdp.management.helpers.TestgCqlSessionBuilder;
import com.datastax.bdp.util.ShellUtils;
import com.datastax.bdp.util.SocketUtils;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.auth.AuthenticationException;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.internal.core.auth.PlainTextAuthProvider;
import org.apache.http.HttpStatus;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.AUTH_PROVIDER_CLASS;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.AUTH_PROVIDER_PASSWORD;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.AUTH_PROVIDER_USER_NAME;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class IntegrationTest
{
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testKeepAlive() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        String mgmtSock = SocketUtils.makeValidUnixSocketFile(null, "management-inttest-keepAlive-mgmt");
        new File(mgmtSock).deleteOnExit();
        String dseSock = SocketUtils.makeValidUnixSocketFile(null, "management-inttest-keepAlive-dse");
        new File(dseSock).deleteOnExit();


        List<String> extraArgs = IntegrationTestUtils.getExtraArgs(IntegrationTest.class, "testKeepAlive", temporaryFolder.getRoot());

        Cli cli = new Cli(Collections.singletonList("file://" + mgmtSock), IntegrationTestUtils.getCassandraExe(), dseSock, true, extraArgs);

        cli.preflightChecks();
        Thread cliThread = new Thread(cli);

        try
        {
            cliThread.start();

            NettyHttpIPCClient client = new NettyHttpIPCClient(mgmtSock);

            //Verify liveness
            boolean live = client.get(URI.create("http://localhost/api/v0/probes/liveness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertTrue(live);


            boolean ready = false;
            int tries = 0;
            while (tries++ < 10)
            {
                ready = client.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                        .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

                if (ready)
                    break;

                Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
            }

            assertTrue(ready);


            //Kill the DSE Process and check it restarts automatically
            Integer pid = client.get(URI.create("http://localhost/api/v0/lifecycle/pid").toURL())
                    .thenApply(r -> {
                        if (r.status().code() == HttpStatus.SC_OK)
                        {
                            byte[] pidBytes = new byte[r.content().readableBytes()];
                            r.content().readBytes(pidBytes);

                            return Integer.valueOf(new String(pidBytes));
                        }

                        return null;
                    }).join();

            assertNotNull(pid);

            boolean didKill = ShellUtils.executeShellWithHandlers("kill -9 " + pid,
                    (input, err) -> true,
                    (exitcode, err) -> false);

            System.err.println("Killed pid " + pid + " " + true);
            assertTrue(didKill);

            //Cleanup the file from the kill -9
            assertTrue(new File(dseSock).delete());

            ready = client.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertFalse(ready);

            //Wait for restart... takes a while...
            tries = 0;
            while (tries++ < 20)
            {
                ready = client.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                        .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

                if (ready)
                    break;

                Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
            }

            //Verify auto restart
            assertTrue(ready);


            //Now Stop
            boolean stopped = client.post(URI.create("http://localhost/api/v0/lifecycle/stop").toURL(), null)
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertTrue(stopped);

            tries = 0;
            while (tries++ < 6)
            {
                ready = client.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                        .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

                if (!ready)
                    break;

                Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
            }

            assertFalse(ready);

            Uninterruptibles.sleepUninterruptibly(20, TimeUnit.SECONDS);

            //Check it's not restarted automatically when requested stopped state
            ready = client.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertFalse(ready);
        }
        finally
        {
            cli.stop();
            FileUtils.deleteQuietly(new File(dseSock));
            FileUtils.deleteQuietly(new File(mgmtSock));
        }
    }

    @Test
    public void testLifecycle() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        String mgmtSock = SocketUtils.makeValidUnixSocketFile(null, "management-inttest-lifecycle-mgmt");
        new File(mgmtSock).deleteOnExit();
        String dseSock = SocketUtils.makeValidUnixSocketFile(null, "management-inttest-lifecycle-dse");
        new File(dseSock).deleteOnExit();

        List<String> extraArgs = IntegrationTestUtils.getExtraArgs(IntegrationTest.class, "testLifecycle", temporaryFolder.getRoot());

        Cli cli = new Cli(Collections.singletonList("file://" + mgmtSock), IntegrationTestUtils.getCassandraExe(), dseSock, false, extraArgs);

        cli.preflightChecks();
        Thread cliThread = new Thread(cli);

        try
        {
            cliThread.start();

            NettyHttpIPCClient client = new NettyHttpIPCClient(mgmtSock);

            //Verify liveness
            boolean live = client.get(URI.create("http://localhost/api/v0/probes/liveness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertTrue(live);

            //Verify readiness fails
            boolean ready = client.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertFalse(ready);

            //Startup
            boolean started = client.post(URI.create("http://localhost/api/v0/lifecycle/start").toURL(), null)
                    .thenApply(r -> r.status().code() == HttpStatus.SC_CREATED).join();

            assertTrue(started);

            int tries = 0;
            while (tries++ < 10)
            {
                ready = client.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                        .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

                if (ready)
                    break;

                Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
            }

            assertTrue(ready);

            // Check that start is idempotent
            started = client.post(URI.create("http://localhost/api/v0/lifecycle/start").toURL(), null)
                    .thenApply(r -> r.status().code() == HttpStatus.SC_ACCEPTED).join();

            assertTrue(started);


            //Now Stop
            boolean stopped = client.post(URI.create("http://localhost/api/v0/lifecycle/stop").toURL(), null)
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertTrue(stopped);

            tries = 0;
            while (tries++ < 10)
            {
                ready = client.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                        .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

                if (!ready)
                    break;

                Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
            }

            assertFalse(ready);

            //Check that stop is idempotent
            stopped = client.post(URI.create("http://localhost/api/v0/lifecycle/stop").toURL(), null)
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertTrue(stopped);
        }
        finally
        {
            cli.stop();
            FileUtils.deleteQuietly(new File(dseSock));
            FileUtils.deleteQuietly(new File(mgmtSock));
        }
    }

    @Test
    public void testSuperuserWasNotSet() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        String mgmtSock = SocketUtils.makeValidUnixSocketFile(null, "management-inttest-lifecycle-mgmt");
        new File(mgmtSock).deleteOnExit();
        String dseSock = SocketUtils.makeValidUnixSocketFile(null, "management-inttest-lifecycle-dse");
        new File(dseSock).deleteOnExit();

        int offset = ThreadLocalRandom.current().nextInt(1024);
        List<String> extraArgs = IntegrationTestUtils.getExtraArgs(IntegrationTest.class, "testSuperuserWasNotSet", temporaryFolder.getRoot(), offset);

        Cli cli = new Cli(Collections.singletonList("file://" + mgmtSock), IntegrationTestUtils.getCassandraExe(), dseSock, false, extraArgs);

        cli.preflightChecks();
        Thread cliThread = new Thread(cli);

        boolean ready = false;
        NettyHttpIPCClient client = null;
        try
        {
            cliThread.start();

            client = new NettyHttpIPCClient(mgmtSock);

            //Startup
            boolean started = client.post(URI.create("http://localhost/api/v0/lifecycle/start").toURL(), null)
                    .thenApply(r -> r.status().code() == HttpStatus.SC_CREATED).join();

            assertTrue(started);

            int tries = 0;
            while (tries++ < 10)
            {
                ready = client.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                        .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

                if (ready)
                    break;

                Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
            }

            assertTrue(ready);

            try
            {
                // verify that we can't login with user cassandra/cassandra
                CqlSession session =  new TestgCqlSessionBuilder()
                        .withConfigLoader(DriverConfigLoader.programmaticBuilder()
                                .withString(AUTH_PROVIDER_CLASS, PlainTextAuthProvider.class.getCanonicalName())
                                .withString(AUTH_PROVIDER_USER_NAME, "cassandrajjj")
                                .withString(AUTH_PROVIDER_PASSWORD, "cassandra")
                                .withString(LOAD_BALANCING_LOCAL_DATACENTER, "Cassandra")
                                .build())
                        .addContactPoint(new InetSocketAddress("127.0.0.1", 9042 + offset))
                        .build();

                    fail("Session builder should fail with AuthenticationException");
            }
            catch (Exception e)
            {
                assertEquals(e.getClass(), AllNodesFailedException.class);
                Throwable t = ((AllNodesFailedException) e).getErrors().values().iterator().next();
                assertTrue(t instanceof AuthenticationException);
            }
        }
        finally
        {
            if (client != null)
                client.post(URI.create("http://localhost/api/v0/lifecycle/stop").toURL(), null).join();
            cli.stop();
            FileUtils.deleteQuietly(new File(dseSock));
            FileUtils.deleteQuietly(new File(mgmtSock));
        }
    }
}
