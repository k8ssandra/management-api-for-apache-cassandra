/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static com.datastax.mgmtapi.BaseDockerIntegrationTest.BASE_PATH;
import static com.datastax.mgmtapi.BaseDockerIntegrationTest.BASE_PATH_V1;
import static com.datastax.mgmtapi.BaseDockerIntegrationTest.BASE_URL;
import static com.datastax.mgmtapi.BaseDockerIntegrationTest.JSON_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.resources.models.Job;
import com.datastax.mgmtapi.resources.models.RepairRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.netty.util.IllegalReferenceCountException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AsyncRepairIT extends BaseDockerIntegrationTest {

  public AsyncRepairIT(String version) throws IOException {
    super(version);
  }

  @Test
  public void testAsyncRepair() throws IOException, URISyntaxException, InterruptedException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    // create a keyspace with RF of at least 2
    NettyHttpClient client = new NettyHttpClient(BASE_URL);
    String localDc =
        client
            .get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
            .thenApply(this::responseAsString)
            .join();

    String ks = "someTestKeyspace";
    createKeyspace(client, localDc, ks, 2);

    URIBuilder uriBuilder = new URIBuilder(BASE_PATH_V1 + "/ops/node/repair");
    URI repairUri = uriBuilder.build();

    // execute repair
    RepairRequest repairRequest = new RepairRequest("someTestKeyspace", null, Boolean.TRUE);
    String requestAsJSON = JSON_MAPPER.writeValueAsString(repairRequest);

    Pair<Integer, String> repairResponse =
        client.post(repairUri.toURL(), requestAsJSON).thenApply(this::responseAsCodeAndBody).join();
    assertThat(repairResponse.getLeft()).isEqualTo(HttpStatus.SC_ACCEPTED);
    String jobId = repairResponse.getRight();
    assertThat(jobId).isNotEmpty();

    URI getJobDetailsUri =
        new URIBuilder(BASE_PATH + "/ops/executor/job").addParameter("job_id", jobId).build();

    await()
        .atMost(Duration.ofMinutes(5))
        .untilAsserted(
            () -> {
              Pair<Integer, String> getJobDetailsResponse;
              try {
                getJobDetailsResponse =
                    client
                        .get(getJobDetailsUri.toURL())
                        .thenApply(this::responseAsCodeAndBody)
                        .join();
              } catch (IllegalReferenceCountException e) {
                // Just retry
                assertFalse(true);
                return;
              }
              assertThat(getJobDetailsResponse.getLeft()).isEqualTo(HttpStatus.SC_OK);
              Job jobDetails =
                  new JsonMapper()
                      .readValue(getJobDetailsResponse.getRight(), new TypeReference<Job>() {});
              assertThat(jobDetails.getJobId()).isEqualTo(jobId);
              assertThat(jobDetails.getJobType()).isEqualTo("repair");
              assertThat(jobDetails.getStatus().toString()).isIn("COMPLETED", "ERROR");
            });
  }
}
