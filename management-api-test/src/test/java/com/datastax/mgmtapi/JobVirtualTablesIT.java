/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.helpers.TestgCqlSessionBuilder;
import com.datastax.mgmtapi.resources.models.RepairRequest;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Queries agent job state over the regular native port while NodeOps runs over the Unix socket. */
@RunWith(Parameterized.class)
public class JobVirtualTablesIT extends BaseDockerIsolatedIntegrationTest {
  public JobVirtualTablesIT(String version) throws IOException {
    super(version);
  }

  @Parameterized.Parameters(name = "{index}: {0}")
  public static List<String> testVersions() {
    return BaseDockerIntegrationTest.testVersions().stream()
        .filter(v -> v.startsWith("5_") || v.startsWith("6_") || v.startsWith("trunk"))
        .collect(Collectors.toList());
  }

  @Test
  public void queriesLocalJobsAndHistoryThroughNativeTransport()
      throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();
    NettyHttpClient client = getClient();
    String localDc =
        client
            .get(URI.create(BASE_PATH + "/metadata/localdc").toURL())
            .thenApply(this::responseAsString)
            .join();

    try (CqlSession session =
        new TestgCqlSessionBuilder()
            .withLocalDatacenter(localDc)
            .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
            .build()) {
      assertThat(session.execute("SELECT * FROM management_api.jobs").all()).isEmpty();
      assertThat(session.execute("SELECT * FROM management_api.job_events").all()).isEmpty();
      assertThat(session.execute("SELECT * FROM system_views.settings LIMIT 1").one()).isNotNull();

      // Reuse the REST/Unix-socket repair path to populate the real executor's cache.
      String keyspace = "virtual_table_test";
      createKeyspace(client, localDc, keyspace, 2);
      Pair<Integer, String> repairResponse =
          client
              .post(
                  URI.create(BASE_PATH_V1 + "/ops/node/repair").toURL(),
                  JSON_MAPPER.writeValueAsString(new RepairRequest(keyspace, null, Boolean.TRUE)))
              .thenApply(this::responseAsCodeAndBody)
              .join();
      assertThat(repairResponse.getLeft()).isEqualTo(HttpStatus.SC_ACCEPTED);
      String jobId = repairResponse.getRight();
      assertThat(jobId).isNotEmpty();

      await()
          .atMost(Duration.ofMinutes(5))
          .untilAsserted(
              () -> {
                List<Row> rows =
                    session
                        .execute("SELECT * FROM management_api.jobs WHERE job_id = ?", jobId)
                        .all();
                assertThat(rows).hasSize(1);
                Row job = rows.get(0);
                assertThat(job.getString("job_id")).isEqualTo(jobId);
                assertThat(job.getString("job_type")).isEqualTo("repair");
                assertThat(job.getString("status")).isIn("COMPLETED", "ERROR");
                assertThat(job.getInstant("submitted_at")).isNotNull();
                assertThat(job.getInstant("finished_at")).isNotNull();
                assertThat(
                        session
                            .execute(
                                "SELECT * FROM management_api.job_events WHERE job_id = ?", jobId)
                            .all())
                    .isNotEmpty()
                    .allSatisfy(
                        event -> {
                          assertThat(event.getString("job_id")).isEqualTo(jobId);
                          assertThat(event.getInstant("event_time")).isNotNull();
                          assertThat(event.getString("event_type")).isNotEmpty();
                        });
              });

      assertThat(
              session.execute("SELECT * FROM management_api.jobs WHERE job_id = 'missing'").all())
          .isEmpty();
      assertThat(
              session
                  .execute("SELECT * FROM management_api.job_events WHERE job_id = 'missing'")
                  .all())
          .isEmpty();
      assertThrows(
          InvalidQueryException.class,
          () ->
              session.execute(
                  "INSERT INTO management_api.jobs (job_id, status) VALUES ('x', 'COMPLETED')"));
      assertThrows(
          InvalidQueryException.class,
          () ->
              session.execute(
                  "INSERT INTO management_api.job_events (job_id, event_time, message)"
                      + " VALUES ('x', 1000, 'fake')"));
      assertThrows(
          InvalidQueryException.class,
          () -> session.execute("DELETE FROM management_api.jobs WHERE job_id = 'x'"));
      assertThrows(
          InvalidQueryException.class, () -> session.execute("TRUNCATE management_api.jobs"));
      assertThrows(
          InvalidQueryException.class, () -> session.execute("TRUNCATE management_api.job_events"));
    }
  }
}
