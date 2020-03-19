package com.datastax.mgmtapi;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.datastax.mgmtapi.helpers.DockerHelper;
import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.util.ShellUtils;
import org.apache.http.HttpStatus;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class KeepAliveIntegrationTest extends BaseDockerIntegrationTest
{
    public KeepAliveIntegrationTest(String version)
    {
        super(version);
    }

    protected ArrayList<String> getEnvironmentVars()
    {
        return Lists.newArrayList();
    }

    @Test
    public void testKeepAlive() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        NettyHttpClient client = getClient();

        //Verify liveness
        boolean live = client.get(URI.create(BASE_PATH + "/probes/liveness").toURL())
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

        assertTrue(live);

        boolean ready = false;
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

        //Kill the DSE Process and check it restarts automatically
        Integer pid = client.get(URI.create(BASE_PATH + "/lifecycle/pid").toURL())
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

        //Will throw if fails
        docker.waitTillFinished(docker.runCommand("kill", "-9", String.valueOf(pid)));

        Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);

        ready = client.get(URI.create(BASE_PATH + "/probes/readiness").toURL())
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

        assertFalse(ready);

        //Wait for restart... takes a while...
        tries = 0;
        while (tries++ < 20)
        {
            ready = client.get(URI.create(BASE_PATH + "/probes/readiness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            if (ready)
                break;

            Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
        }

        //Verify auto restart
        assertTrue(ready);

        //Now Stop
        boolean stopped = client.post(URI.create(BASE_PATH + "/lifecycle/stop").toURL(), null)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

        assertTrue(stopped);

        tries = 0;
        while (tries++ < 6)
        {
            ready = client.get(URI.create(BASE_PATH + "/probes/readiness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            if (!ready)
                break;

            Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
        }

        assertFalse(ready);

        Uninterruptibles.sleepUninterruptibly(20, TimeUnit.SECONDS);

        //Check it's not restarted automatically when requested stopped state
        ready = client.get(URI.create(BASE_PATH + "/probes/readiness").toURL())
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

        assertFalse(ready);
    }
}
