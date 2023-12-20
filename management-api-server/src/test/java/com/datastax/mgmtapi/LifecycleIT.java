/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.AUTH_PROVIDER_CLASS;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.AUTH_PROVIDER_PASSWORD;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.AUTH_PROVIDER_USER_NAME;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.helpers.TestgCqlSessionBuilder;
import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.auth.AuthenticationException;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.internal.core.auth.PlainTextAuthProvider;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LifecycleIT extends BaseDockerIsolatedIntegrationTest {
  public LifecycleIT(String version) throws IOException {
    super(version);
  }

  @Test
  public void testLifecycle() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());

    try {
      NettyHttpClient client = getClient();

      // Verify liveness
      boolean live =
          client
              .get(URI.create(BASE_PATH + "/probes/liveness").toURL())
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      assertTrue(live);

      // Verify readiness fails
      boolean ready =
          client
              .get(URI.create(BASE_PATH + "/probes/readiness").toURL())
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      assertFalse(ready);

      // Startup
      boolean started =
          client
              .post(URI.create(BASE_PATH + "/lifecycle/start").toURL(), null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_CREATED)
              .join();

      assertTrue(started);

      int tries = 0;
      while (tries++ < 10) {
        ready =
            client
                .get(URI.create(BASE_PATH + "/probes/readiness").toURL())
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
                .join();

        if (ready) break;

        Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
      }

      assertTrue(ready);

      // Check that start is idempotent
      started =
          client
              .post(URI.create(BASE_PATH + "/lifecycle/start").toURL(), null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_ACCEPTED)
              .join();

      assertTrue(started);

      // Now Stop
      boolean stopped =
          client
              .post(URI.create(BASE_PATH + "/lifecycle/stop").toURL(), null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      assertTrue(stopped);

      tries = 0;
      while (tries++ < 10) {
        ready =
            client
                .get(URI.create(BASE_PATH + "/probes/readiness").toURL())
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
                .join();

        if (!ready) break;

        Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
      }

      assertFalse(ready);

      // Check that stop is idempotent
      stopped =
          client
              .post(URI.create(BASE_PATH + "/lifecycle/stop").toURL(), null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      assertTrue(stopped);
    } finally {

    }
  }

  @Test
  public void testSuperuserWasNotSet() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());

    boolean ready = false;
    NettyHttpClient client = null;
    try {
      client = getClient();

      // Configure
      boolean configured =
          client
              .post(
                  URI.create(BASE_PATH + "/lifecycle/configure?profile=authtest").toURL(),
                  FileUtils.readFileToString(
                      IntegrationTestUtils.getFile(this.getClass(), "operator-sample.yaml")),
                  "application/yaml")
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      assertTrue(configured);

      // Startup
      boolean started =
          client
              .post(URI.create(BASE_PATH + "/lifecycle/start?profile=authtest").toURL(), null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_CREATED)
              .join();

      assertTrue(started);

      int tries = 0;
      while (tries++ < 10) {
        ready =
            client
                .get(URI.create(BASE_PATH + "/probes/readiness").toURL())
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
                .join();

        if (ready) break;

        Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
      }

      assertTrue(ready);

      try {
        // verify that we can't login with user cassandra/cassandra
        CqlSession session =
            new TestgCqlSessionBuilder()
                .withConfigLoader(
                    DriverConfigLoader.programmaticBuilder()
                        .withString(
                            AUTH_PROVIDER_CLASS, PlainTextAuthProvider.class.getCanonicalName())
                        .withString(AUTH_PROVIDER_USER_NAME, "cassandra")
                        .withString(AUTH_PROVIDER_PASSWORD, "cassandra")
                        .withString(LOAD_BALANCING_LOCAL_DATACENTER, "dc1")
                        .build())
                .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
                .build();

        fail("Session builder should fail with AuthenticationException");
      } catch (Exception e) {
        assertEquals(e.getClass(), AllNodesFailedException.class);
        Throwable t = ((AllNodesFailedException) e).getErrors().values().iterator().next();
        assertTrue(t instanceof AuthenticationException);
      }

      // addRole
      boolean roleAdded =
          client
              .post(
                  URI.create(
                          BASE_PATH
                              + "/ops/auth/role?username=authtest&password=authtest&is_superuser=true&can_login=true")
                      .toURL(),
                  null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      assertTrue(roleAdded);

      // verify that we can login with user authtest/authtest
      CqlSession session =
          new TestgCqlSessionBuilder()
              .withConfigLoader(
                  DriverConfigLoader.programmaticBuilder()
                      .withString(
                          AUTH_PROVIDER_CLASS, PlainTextAuthProvider.class.getCanonicalName())
                      .withString(AUTH_PROVIDER_USER_NAME, "authtest")
                      .withString(AUTH_PROVIDER_PASSWORD, "authtest")
                      .withString(LOAD_BALANCING_LOCAL_DATACENTER, "dc1")
                      .build())
              .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
              .build();

      for (String systemKeyspace :
          Arrays.asList("system_auth", "system_distributed", "system_traces")) {
        ResultSet rs =
            session.execute(
                String.format(
                    "select replication from system_schema.keyspaces where keyspace_name='%s'",
                    systemKeyspace));

        Map<String, String> params = rs.one().getMap("replication", String.class, String.class);
        assertEquals("1", params.get("dc1"));
      }

    } finally {
      // Stop before next test starts
      boolean stopped =
          client
              .post(URI.create("http://localhost/api/v0/lifecycle/stop").toURL(), null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      assertTrue(stopped);
    }
  }

  @Test
  public void testDcReplicationFactorOverrides() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());

    boolean ready = false;
    NettyHttpClient client = null;
    try {
      client = getClient();

      // Configure
      boolean configured =
          client
              .post(
                  URI.create(BASE_PATH + "/lifecycle/configure?profile=dcrftest").toURL(),
                  FileUtils.readFileToString(
                      IntegrationTestUtils.getFile(this.getClass(), "dcrf-override-1.yaml")),
                  "application/yaml")
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      assertTrue(configured);

      // Startup
      boolean started =
          client
              .post(URI.create(BASE_PATH + "/lifecycle/start?profile=dcrftest").toURL(), null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_CREATED)
              .join();

      assertTrue(started);

      int tries = 0;
      while (tries++ < 10) {
        ready =
            client
                .get(URI.create(BASE_PATH + "/probes/readiness").toURL())
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
                .join();

        if (ready) break;

        Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
      }

      assertTrue(ready);

      // addRole
      boolean roleAdded =
          client
              .post(
                  URI.create(
                          BASE_PATH
                              + "/ops/auth/role?username=dcrftest&password=dcrftest&is_superuser=true&can_login=true")
                      .toURL(),
                  null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      // create a session
      CqlSession session =
          new TestgCqlSessionBuilder()
              .withConfigLoader(
                  DriverConfigLoader.programmaticBuilder()
                      .withString(
                          AUTH_PROVIDER_CLASS, PlainTextAuthProvider.class.getCanonicalName())
                      .withString(AUTH_PROVIDER_USER_NAME, "dcrftest")
                      .withString(AUTH_PROVIDER_PASSWORD, "dcrftest")
                      .withString(LOAD_BALANCING_LOCAL_DATACENTER, "dc1")
                      .build())
              .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
              .build();

      for (String systemKeyspace :
          Arrays.asList("system_auth", "system_distributed", "system_traces")) {
        ResultSet rs =
            session.execute(
                String.format(
                    "select replication from system_schema.keyspaces where keyspace_name='%s'",
                    systemKeyspace));

        Map<String, String> params = rs.one().getMap("replication", String.class, String.class);
        assertEquals("1", params.get("dc1"));
      }
    } finally {
      // Stop before next test starts
      boolean stopped =
          client
              .post(URI.create("http://localhost/api/v0/lifecycle/stop").toURL(), null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      assertTrue(stopped);
    }
  }
}
