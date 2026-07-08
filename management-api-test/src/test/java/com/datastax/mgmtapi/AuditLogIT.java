/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static com.datastax.mgmtapi.BaseDockerIntegrationTest.BASE_PATH;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AuditLogIT extends BaseDockerIsolatedIntegrationTest {

  public AuditLogIT(String version) throws IOException {
    super(version);
  }

  @Test
  public void testAuditLogs() throws IOException, InterruptedException {
    assumeTrue(IntegrationTestUtils.shouldRun() && !this.version.startsWith("dse"));

    boolean ready = false;
    NettyHttpClient client;
    try {
      client = getClient();

      // Configure
      boolean configured =
          client
              .post(
                  URI.create(BASE_PATH + "/lifecycle/configure?profile=auditlogtest").toURL(),
                  FileUtils.readFileToString(
                      IntegrationTestUtils.getFile(this.getClass(), "audit-log-test.yaml")),
                  "application/yaml")
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      assertTrue(configured);

      // Startup
      boolean started =
          client
              .post(URI.create(BASE_PATH + "/lifecycle/start?profile=auditlogtest").toURL(), null)
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

        if (ready) {
          break;
        }

        Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
      }

      assertTrue(ready);

      // make API call to get endpoints
      client
          .get(URI.create(BASE_PATH + "/metadata/endpoints").toURL())
          .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
          .join();

      String systemLogTail = "EMPTY";
      try {
        systemLogTail = docker.runCommandWithOutput("tail", "-50", "/var/log/cassandra/system.log");
        docker.runCommand(
            "grep", "-in", "\"Failed notifying listeners\"", "/var/log/cassandra/system.log");
      } catch (IOException ioe) {
        // grep should NOt find anything and thus will fail with a non-zero rc
      } catch (Throwable t) {
        // running commands failed some way other than a non-zero rc
        System.err.println("Tail of system.log\n" + systemLogTail + "\nEND TAIL");
        t.printStackTrace();
        fail("Checking system.log for audit log errors failed");
      }
      // ensure the system.log does not have "Failed notifying listeners"
      if (systemLogTail.contains("Failed notifying listeners")) {
        // dump the system.log tail
        System.err.println("Tail of system.log\n" + systemLogTail + "\nEND TAIL");
        fail("Audit logs generated an unexpected exception");
      }
    } finally {
      // Stop already called after each test ends
    }
  }
}
