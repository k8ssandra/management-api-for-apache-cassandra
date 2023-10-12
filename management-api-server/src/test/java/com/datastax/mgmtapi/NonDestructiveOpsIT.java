/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.resources.models.CompactRequest;
import com.datastax.mgmtapi.resources.models.CreateOrAlterKeyspaceRequest;
import com.datastax.mgmtapi.resources.models.CreateTableRequest;
import com.datastax.mgmtapi.resources.models.CreateTableRequest.Column;
import com.datastax.mgmtapi.resources.models.CreateTableRequest.ColumnKind;
import com.datastax.mgmtapi.resources.models.Job;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import com.datastax.mgmtapi.resources.models.RepairRequest;
import com.datastax.mgmtapi.resources.models.ReplicationSetting;
import com.datastax.mgmtapi.resources.models.ScrubRequest;
import com.datastax.mgmtapi.resources.models.Table;
import com.datastax.mgmtapi.resources.models.TakeSnapshotRequest;
import com.datastax.mgmtapi.resources.v2.models.RepairParallelism;
import com.datastax.mgmtapi.resources.v2.models.RepairRequestResponse;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Uninterruptibles;
import io.netty.util.IllegalReferenceCountException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for integration testing non-destructive actions. By non-destructive this means actions that
 * do not leave a node in an inoperable state (e.g. assassinate or decommission). The purpose of
 * this is to speed up testing by starting the Cassandra node once, running all tests, and then
 * stopping rather than a start/stop during each test case.
 */
@RunWith(Parameterized.class)
public class NonDestructiveOpsIT extends BaseDockerIntegrationTest {
  private static final Logger logger = LoggerFactory.getLogger(NonDestructiveOpsIT.class);

  public NonDestructiveOpsIT(String version) throws IOException {
    super(version);
  }

  public static void ensureStarted() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    // Verify liveness
    boolean live =
        client
            .get(URI.create(BASE_PATH + "/probes/liveness").toURL())
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();

    assertTrue(live);

    boolean ready = false;

    // Startup
    boolean started =
        client
            .post(URI.create(BASE_PATH + "/lifecycle/start").toURL(), null)
            .thenApply(
                r ->
                    r.status().code() == HttpStatus.SC_CREATED
                        || r.status().code() == HttpStatus.SC_ACCEPTED)
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

      Uninterruptibles.sleepUninterruptibly(10, SECONDS);
    }

    logger.info("CASSANDRA ALIVE: {}", ready);
    assertTrue(ready);
  }

  @Test
  public void testSeedReload() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());

    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    String requestSuccessful =
        client
            .post(URI.create(BASE_PATH + "/ops/seeds/reload").toURL(), null)
            .thenApply(
                r -> {
                  return responseAsString(r);
                })
            .join();

    // Empty because getSeeds removes local node
    assertEquals("[]", requestSuccessful);
  }

  @Test
  public void testConsistencyCheck() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    Integer code =
        client
            .get(URI.create(BASE_PATH + "/probes/cluster?consistency_level=ONE").toURL())
            .thenApply(
                r -> {
                  byte[] versionBytes = new byte[r.content().readableBytes()];
                  r.content().readBytes(versionBytes);
                  logger.info(new String(versionBytes));

                  return r.status().code();
                })
            .join();

    assertEquals(200, (int) code);

    code =
        client
            .get(URI.create(BASE_PATH + "/probes/cluster?consistency_level=QUORUM").toURL())
            .thenApply(
                r -> {
                  byte[] versionBytes = new byte[r.content().readableBytes()];
                  r.content().readBytes(versionBytes);
                  logger.info(new String(versionBytes));

                  return r.status().code();
                })
            .join();

    assertEquals(500, (int) code);

    code =
        client
            .get(
                URI.create(BASE_PATH + "/probes/cluster?consistency_level=QUORUM&rf_per_dc=1")
                    .toURL())
            .thenApply(
                r -> {
                  byte[] versionBytes = new byte[r.content().readableBytes()];
                  r.content().readBytes(versionBytes);
                  logger.info(new String(versionBytes));

                  return r.status().code();
                })
            .join();

    assertEquals(200, (int) code);
  }

  @Test
  public void testSetCompactionThroughput() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URI uri = new URIBuilder(BASE_PATH + "/ops/node/compaction").addParameter("value", "5").build();
    boolean requestSuccessful =
        client.post(uri.toURL(), null).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testSetLoggingLevel() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URI uri =
        new URIBuilder(BASE_PATH + "/ops/node/logging")
            .addParameter("target", "cql")
            .addParameter("rawLevel", "debug")
            .build();
    boolean requestSuccessful =
        client.post(uri.toURL(), null).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testTruncateWithHost() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    // try IP of container
    URI uri =
        new URIBuilder(BASE_PATH + "/ops/node/hints/truncate")
            .addParameter("host", docker.getIpAddressOfContainer())
            .build();
    boolean requestSuccessful =
        client.post(uri.toURL(), null).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

    if (!requestSuccessful) {
      // try 127.0.0.1
      uri =
          new URIBuilder(BASE_PATH + "/ops/node/hints/truncate")
              .addParameter("host", "127.0.0.1")
              .build();
      requestSuccessful =
          client
              .post(uri.toURL(), null)
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();
      assertTrue(requestSuccessful);
    }
  }

  @Test
  public void testTruncateWithoutHost() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URI uri = new URIBuilder(BASE_PATH + "/ops/node/hints/truncate").build();
    boolean requestSuccessful =
        client.post(uri.toURL(), null).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testResetLocalSchema() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    // Reset schema does not work on Cassandra 4.1+
    assumeFalse("4_1".equals(this.version));
    assumeFalse("4_1_ubi".equals(this.version));
    assumeFalse("trunk".equals(this.version));
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URI uri = new URIBuilder(BASE_PATH + "/ops/node/schema/reset").build();
    boolean requestSuccessful =
        client.post(uri.toURL(), null).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testReloadLocalSchema() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URI uri = new URIBuilder(BASE_PATH + "/ops/node/schema/reload").build();
    boolean requestSuccessful =
        client.post(uri.toURL(), null).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testGetReleaseVersion() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URI uri = new URIBuilder(BASE_PATH + "/metadata/versions/release").build();
    String response =
        client
            .get(uri.toURL())
            .thenApply(
                r -> {
                  return responseAsString(r);
                })
            .join();
    assertNotNull(response);
    assertNotEquals("", response);
  }

  @Test
  public void testGetEndpoints() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URI uri = new URIBuilder(BASE_PATH + "/metadata/endpoints").build();
    String responseJson =
        client
            .get(uri.toURL())
            .thenApply(
                r -> {
                  return responseAsString(r);
                })
            .join();

    assertNotNull(responseJson);
    assertNotEquals("", responseJson);

    Map<String, Object> response =
        new ObjectMapper().readValue(responseJson, new TypeReference<Map<String, Object>>() {});
    @SuppressWarnings("unchecked")
    List<Map<String, String>> entity = (List<Map<String, String>>) response.get("entity");
    Map<String, String> endpoint = entity.get(0);
    assertThat(endpoint.get("PARTITIONER")).endsWith("Murmur3Partitioner");
    assertThat(endpoint.get("CLUSTER_NAME")).matches("Test Cluster");
    assertThat(endpoint.get("IS_LOCAL")).isEqualTo("true");
    Iterable<String> tokens = Splitter.on(",").split(endpoint.get("TOKENS"));
    assertThat(tokens)
        .allSatisfy(
            token -> assertThatCode(() -> Long.parseLong(token)).doesNotThrowAnyException());
  }

  @Test
  public void testCleanup() throws IOException, URISyntaxException, InterruptedException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    KeyspaceRequest keyspaceRequest =
        new KeyspaceRequest(1, "system_traces", Collections.singletonList("events"));
    String keyspaceRequestAsJSON = JSON_MAPPER.writeValueAsString(keyspaceRequest);
    URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace/cleanup").build();

    // Get job_id here..
    Pair<Integer, String> postResponse =
        client
            .post(uri.toURL(), keyspaceRequestAsJSON)
            .thenApply(this::responseAsCodeAndBody)
            .join();
    assertEquals(HttpStatus.SC_OK, postResponse.getLeft().longValue());

    String jobId = postResponse.getRight();
    assertNotNull(jobId); // If return code != OK, this is null

    // Add here the check for the job and that is actually is set to complete..
    Job currentStatus = null;
    for (int i = 0; i < 10; i++) {
      URI uriJobStatus = new URIBuilder(BASE_PATH + "/ops/executor/job?job_id=" + jobId).build();
      currentStatus =
          client
              .get(uriJobStatus.toURL())
              .thenApply(
                  re -> {
                    String jobJson = responseAsString(re);
                    try {
                      return new ObjectMapper().readValue(jobJson, Job.class);
                    } catch (JsonProcessingException e) {
                      fail();
                    }
                    return null;
                  })
              .join();
      if (currentStatus != null) {
        if (currentStatus.getStatus() == Job.JobStatus.COMPLETED) {
          break;
        }
      }
      Thread.sleep(100);
    }

    assertNotNull(currentStatus);
    assertEquals(jobId, currentStatus.getJobId());
    assertEquals(Job.JobStatus.COMPLETED, currentStatus.getStatus());
    assertEquals("CLEANUP", currentStatus.getJobType());
  }

  @Test
  public void testRefresh() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URI uri =
        new URIBuilder(BASE_PATH + "/ops/keyspace/refresh")
            .addParameter("keyspaceName", "system_traces")
            .addParameter("table", "events")
            .addParameter("resetLevels", "true")
            .build();
    boolean requestSuccessful =
        client.post(uri.toURL(), null).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testScrub() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    ScrubRequest scrubRequest =
        new ScrubRequest(
            true, true, true, true, 2, "system_traces", Collections.singletonList("events"));
    String requestAsJSON = JSON_MAPPER.writeValueAsString(scrubRequest);
    URI uri = new URIBuilder(BASE_PATH + "/ops/tables/scrub").build();
    boolean requestSuccessful =
        client
            .post(uri.toURL(), requestAsJSON)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testCompact() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    CompactRequest compactRequest =
        new CompactRequest(
            false, false, null, null, "system_traces", null, Collections.singletonList("events"));
    String requestAsJSON = JSON_MAPPER.writeValueAsString(compactRequest);
    URI uri = new URIBuilder(BASE_PATH + "/ops/tables/compact").build();
    boolean requestSuccessful =
        client
            .post(uri.toURL(), requestAsJSON)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testGarbageCollect() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    KeyspaceRequest keyspaceRequest =
        new KeyspaceRequest(1, "system_traces", Collections.singletonList("events"));
    String keyspaceRequestAsJSON = JSON_MAPPER.writeValueAsString(keyspaceRequest);
    URI uri = new URIBuilder(BASE_PATH + "/ops/tables/garbagecollect").build();
    boolean requestSuccessful =
        client
            .post(uri.toURL(), keyspaceRequestAsJSON)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testFlush() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, null, null);
    String keyspaceRequestAsJSON = JSON_MAPPER.writeValueAsString(keyspaceRequest);
    URI uri = new URIBuilder(BASE_PATH + "/ops/tables/flush").build();
    boolean requestSuccessful =
        client
            .post(uri.toURL(), keyspaceRequestAsJSON)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testUpgradeSSTables() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "", null);
    String keyspaceRequestAsJSON = JSON_MAPPER.writeValueAsString(keyspaceRequest);
    URI uri = new URIBuilder(BASE_PATH + "/ops/tables/sstables/upgrade").build();
    boolean requestSuccessful =
        client
            .post(uri.toURL(), keyspaceRequestAsJSON)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testGetStreamInfo() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URI uri = new URIBuilder(BASE_PATH + "/ops/node/streaminfo").build();
    String response = client.get(uri.toURL()).thenApply(this::responseAsString).join();
    assertNotNull(response);
    assertNotEquals("", response);
  }

  @Test
  public void testCreateKeyspace() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);
    String localDc =
        client
            .get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
            .thenApply(this::responseAsString)
            .join();

    createKeyspace(client, localDc, "someTestKeyspace", 1);
  }

  @Test
  public void testAlterKeyspace() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);
    String localDc =
        client
            .get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
            .thenApply(this::responseAsString)
            .join();

    String ks = "alteringKeyspaceTest";
    createKeyspace(client, localDc, ks, 1);

    CreateOrAlterKeyspaceRequest request =
        new CreateOrAlterKeyspaceRequest(ks, Arrays.asList(new ReplicationSetting(localDc, 3)));
    String requestAsJSON = JSON_MAPPER.writeValueAsString(request);

    boolean requestSuccessful =
        client
            .post(new URIBuilder(BASE_PATH + "/ops/keyspace/alter").build().toURL(), requestAsJSON)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(requestSuccessful);
  }

  @Test
  public void testGetKeyspaces() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);
    String localDc =
        client
            .get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
            .thenApply(this::responseAsString)
            .join();

    String ks = "getkeyspacestest";
    createKeyspace(client, localDc, ks, 1);

    URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace").build();
    String response = client.get(uri.toURL()).thenApply(this::responseAsString).join();
    assertNotNull(response);
    assertNotEquals("", response);
    assertTrue(response.contains(ks));

    URI uriFilter = new URIBuilder(BASE_PATH + "/ops/keyspace?keyspaceName=" + ks).build();
    String responseFilter = client.get(uriFilter.toURL()).thenApply(this::responseAsString).join();
    assertNotNull(responseFilter);
    assertNotEquals("", responseFilter);

    final ObjectMapper jsonMapper = new ObjectMapper();
    List<String> keyspaces =
        jsonMapper.readValue(responseFilter, new TypeReference<List<String>>() {});
    assertEquals(1, keyspaces.size());
    assertEquals(ks, keyspaces.get(0));
  }

  @Test
  public void testGetSchemaVersions() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URIBuilder uriBuilder = new URIBuilder(BASE_PATH_V1 + "/ops/node/schema/versions");
    URI uri = uriBuilder.build();

    Pair<Integer, String> response =
        client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);

    // The response body should look something like this,
    //
    //    2207c2a9-f598-3971-986b-2926e09e239d: [10.244.1.4, 10.244.2.3, 10.244.3.3]
    //
    // The uuid is the schema version and list on the right are the nodes at that version. Because
    // are only testing with a single node we should expect the list to contain a single value.

    Map<String, List> actual =
        new JsonMapper().readValue(response.getRight(), new TypeReference<Map<String, List>>() {});
    assertThat(actual).hasSizeGreaterThanOrEqualTo(1);

    List nodes = Lists.emptyList();
    for (Map.Entry<String, List> entry : actual.entrySet()) {
      nodes = entry.getValue();
      break;
    }

    assertThat(nodes.size()).isEqualTo(1);
  }

  @Test
  public void testGetSnapshotDetails()
      throws IOException, URISyntaxException, InterruptedException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URIBuilder uriBuilder = new URIBuilder(BASE_PATH + "/ops/node/snapshots");
    URI takeSnapshotUri = uriBuilder.build();

    // create a snapshot
    TakeSnapshotRequest takeSnapshotRequest =
        new TakeSnapshotRequest(
            "testSnapshot",
            Arrays.asList("system_schema", "system_traces", "system_distributed"),
            null,
            null,
            null);
    String requestAsJSON = JSON_MAPPER.writeValueAsString(takeSnapshotRequest);

    boolean takeSnapshotSuccessful =
        client
            .post(takeSnapshotUri.toURL(), requestAsJSON)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(takeSnapshotSuccessful);

    // get snapshot details
    URI getSnapshotsUri = uriBuilder.addParameter("snapshotNames", "testSnapshot").build();
    String getSnapshotResponse =
        client.get(getSnapshotsUri.toURL()).thenApply(this::responseAsString).join();
    assertNotNull(getSnapshotResponse);
    Object responseObject = JSON_MAPPER.readValue(getSnapshotResponse, Object.class);
    assertTrue(responseObject instanceof Map);
    Map<Object, Object> responseObj = (Map) responseObject;
    assertTrue(responseObj.containsKey("entity"));
    Object entityObj = responseObj.get("entity");
    assertTrue(entityObj instanceof List);
    List<Object> entities = (List<Object>) entityObj;
    assertFalse(entities.isEmpty());
    for (Object entity : entities) {
      assertTrue(entity instanceof Map);
      Map<String, String> entityMap = (Map<String, String>) entity;
      assertTrue(entityMap.containsKey("Snapshot name"));
      String snapshotName = entityMap.get("Snapshot name");
      assertEquals("testSnapshot", snapshotName);
    }

    // delete snapshot
    URI clearSnapshotsUri = uriBuilder.addParameter("snapshotNames", "testSnapshot").build();
    boolean clearSnapshotSuccessful =
        client
            .delete(clearSnapshotsUri.toURL())
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(clearSnapshotSuccessful);

    // verify snapshot deleted
    getSnapshotResponse =
        client.get(getSnapshotsUri.toURL()).thenApply(this::responseAsString).join();
    assertNotNull(getSnapshotResponse);
    responseObject = JSON_MAPPER.readValue(getSnapshotResponse, Object.class);
    assertTrue(responseObject instanceof Map);
    responseObj = (Map) responseObject;
    assertTrue(responseObj.containsKey("entity"));
    entityObj = responseObj.get("entity");
    assertTrue(entityObj instanceof List);
    entities = (List<Object>) entityObj;
    assertTrue(entities.isEmpty());
  }

  @Test
  public void testRepair() throws IOException, URISyntaxException, InterruptedException {
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

    URIBuilder uriBuilder = new URIBuilder(BASE_PATH + "/ops/node/repair");
    URI repairUri = uriBuilder.build();

    // execute repair
    RepairRequest repairRequest = new RepairRequest(ks, null, Boolean.TRUE);
    String requestAsJSON = JSON_MAPPER.writeValueAsString(repairRequest);

    boolean repairSuccessful =
        client
            .post(repairUri.toURL(), requestAsJSON)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue("Repair request was not successful", repairSuccessful);
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
              assertThat(jobDetails.getStatus()).isIn("COMPLETED", "ERROR");
            });
  }

  @Test
  public void testGetReplication() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);
    String localDc =
        client
            .get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
            .thenApply(this::responseAsString)
            .join();

    String ks = "getreplicationtest";
    createKeyspace(client, localDc, ks, 1);

    // missing keyspace
    URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace/replication").build();
    Pair<Integer, String> response =
        client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getRight()).contains("Non-empty 'keyspaceName' must be provided");

    // non existent keyspace
    uri = new URIBuilder(BASE_PATH + "/ops/keyspace/replication?keyspaceName=nonexistent").build();
    response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_NOT_FOUND);
    assertThat(response.getRight()).contains("Keyspace 'nonexistent' does not exist");

    // existing keyspace
    uri = new URIBuilder(BASE_PATH + "/ops/keyspace/replication?keyspaceName=" + ks).build();
    response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);

    Map<String, String> actual =
        new JsonMapper()
            .readValue(response.getRight(), new TypeReference<Map<String, String>>() {});
    assertThat(actual)
        .hasSize(2)
        .containsEntry("class", "org.apache.cassandra.locator.NetworkTopologyStrategy")
        .containsKey(localDc);
  }

  @Test
  public void testGetTables() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    // missing keyspace
    URI uri = new URIBuilder(BASE_PATH + "/ops/tables").build();
    Pair<Integer, String> response =
        client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getRight()).contains("Non-empty 'keyspaceName' must be provided");

    // non existent keyspace
    uri = new URIBuilder(BASE_PATH + "/ops/tables?keyspaceName=nonexistent").build();
    response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);
    assertThat(response.getRight()).isEqualTo("[]");

    // existing keyspace
    uri = new URIBuilder(BASE_PATH + "/ops/tables?keyspaceName=system_schema").build();
    response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);

    List<String> actual =
        new JsonMapper().readValue(response.getRight(), new TypeReference<List<String>>() {});
    assertThat(actual)
        .contains(
            "aggregates",
            "columns",
            "functions",
            "indexes",
            "keyspaces",
            "tables",
            "triggers",
            "types",
            "views");
  }

  @Test
  public void testGetTablesV1() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    // missing keyspace
    URI uri = new URIBuilder(BASE_PATH_V1 + "/ops/tables").build();
    Pair<Integer, String> response =
        client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getRight()).contains("Non-empty 'keyspaceName' must be provided");

    // non existent keyspace
    uri = new URIBuilder(BASE_PATH_V1 + "/ops/tables?keyspaceName=nonexistent").build();
    response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);
    assertThat(response.getRight()).isEqualTo("[]");

    // existing keyspace
    uri = new URIBuilder(BASE_PATH_V1 + "/ops/tables?keyspaceName=system_schema").build();
    response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);

    List<Table> actual =
        new JsonMapper().readValue(response.getRight(), new TypeReference<List<Table>>() {});
    assertThat(actual)
        .extracting("name")
        .contains(
            "aggregates",
            "columns",
            "functions",
            "indexes",
            "keyspaces",
            "tables",
            "triggers",
            "types",
            "views");
    assertThat(actual)
        .allSatisfy(
            table ->
                assertThat(table.compaction)
                    .containsEntry(
                        "class",
                        "org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy"));
  }

  @Test
  public void testCreateTable() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);
    String localDc =
        client
            .get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
            .thenApply(this::responseAsString)
            .join();

    // this test also tests case sensitivity in CQL identifiers.
    String ks = "CreateTableTest";
    createKeyspace(client, localDc, ks, 1);

    CreateTableRequest request =
        new CreateTableRequest(
            ks,
            "Table1",
            ImmutableList.of(
                // having two columns with the same name in different cases can only work if the
                // internal name is being used.
                new Column("pk", "int", ColumnKind.PARTITION_KEY, 0, null),
                new Column("PK", "int", ColumnKind.PARTITION_KEY, 1, null),
                new Column("cc", "timeuuid", ColumnKind.CLUSTERING_COLUMN, 0, ClusteringOrder.ASC),
                new Column("CC", "timeuuid", ColumnKind.CLUSTERING_COLUMN, 1, ClusteringOrder.DESC),
                new Column("v", "list<text>", ColumnKind.REGULAR, 0, null),
                new Column("s", "boolean", ColumnKind.STATIC, 0, null)),
            ImmutableMap.of(
                "bloom_filter_fp_chance",
                "0.01",
                "caching",
                ImmutableMap.of("keys", "ALL", "rows_per_partition", "NONE")));

    JsonMapper jsonMapper = new JsonMapper();

    URI uri = new URIBuilder(BASE_PATH + "/ops/tables/create").build();
    Pair<Integer, String> response =
        client
            .post(uri.toURL(), jsonMapper.writeValueAsString(request))
            .thenApply(this::responseAsCodeAndBody)
            .join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);

    uri = new URIBuilder(BASE_PATH + "/ops/tables?keyspaceName=" + ks).build();
    response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);

    List<String> actual =
        jsonMapper.readValue(response.getRight(), new TypeReference<List<String>>() {});
    assertThat(actual).containsExactly("Table1");
  }

  @Test
  public void testMoveNode() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    URI nodeMoveUri =
        new URIBuilder(BASE_PATH + "/ops/node/move").addParameter("newToken", "1234").build();
    Pair<Integer, String> nodeMoveResponse =
        client.post(nodeMoveUri.toURL(), null).thenApply(this::responseAsCodeAndBody).join();
    assertThat(nodeMoveResponse.getLeft()).isEqualTo(HttpStatus.SC_ACCEPTED);
    String jobId = nodeMoveResponse.getRight();
    assertThat(jobId).isNotEmpty();

    URI getJobDetailsUri =
        new URIBuilder(BASE_PATH + "/ops/executor/job").addParameter("job_id", jobId).build();

    await()
        .atMost(Duration.ofMinutes(5))
        .untilAsserted(
            () -> {
              Pair<Integer, String> getJobDetailsResponse =
                  client
                      .get(getJobDetailsUri.toURL())
                      .thenApply(this::responseAsCodeAndBody)
                      .join();
              assertThat(getJobDetailsResponse.getLeft()).isEqualTo(HttpStatus.SC_OK);
              Job jobDetails =
                  new JsonMapper()
                      .readValue(getJobDetailsResponse.getRight(), new TypeReference<Job>() {});
              assertThat(jobDetails.getJobId()).isEqualTo(jobId);
              assertThat(jobDetails.getJobType()).isEqualTo("move");
              assertThat(jobDetails.getStatus()).isIn("COMPLETED", "ERROR");
            });
  }

  @Test
  public void testEnsureStatusChanges() throws Exception {
    assumeTrue(IntegrationTestUtils.shouldRun());
    ensureStarted();
    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    com.datastax.mgmtapi.resources.v2.models.RepairRequest req =
        new com.datastax.mgmtapi.resources.v2.models.RepairRequest(
            "system_distributed",
            null,
            true,
            true,
            Collections.singletonList(
                new com.datastax.mgmtapi.resources.v2.models.RingRange(-1L, 100L)),
            RepairParallelism.SEQUENTIAL,
            null,
            null);

    logger.info("Sending repair request: {}", req);
    URI repairUri = new URIBuilder(BASE_PATH_V2 + "/repairs").build();
    Pair<Integer, String> repairResp =
        client
            .put(repairUri.toURL(), new ObjectMapper().writeValueAsString(req))
            .thenApply(this::responseAsCodeAndBody)
            .join();
    System.out.println("repairResp was " + repairResp);
    String jobID =
        new ObjectMapper().readValue(repairResp.getRight(), RepairRequestResponse.class).repairID;
    Integer repairID =
        Integer.parseInt(
            jobID.substring(7) // Trimming off "repair-" prefix.
            );
    logger.info("Repair ID: {}", repairID);
    assertThat(repairID).isNotNull();
    assertThat(repairID).isGreaterThan(0);

    URI statusUri =
        new URIBuilder(BASE_PATH_V2 + "/ops/executor/job").addParameter("job_id", jobID).build();
    Pair<Integer, String> statusResp =
        client.get(statusUri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    logger.info("Repair job status: {}", statusResp);
    Job jobStatus = new ObjectMapper().readValue(statusResp.getRight(), Job.class);

    assertThat(jobStatus.getStatus()).isNotNull();
    assertThat(jobStatus.getStatusChanges()).isNotNull();
    await()
        .atMost(5, SECONDS)
        .until(
            () -> {
              Pair<Integer, String> statusResp2 =
                  client.get(statusUri.toURL()).thenApply(this::responseAsCodeAndBody).join();
              logger.info("Repair job status: {}", statusResp);
              Job jobStatus2 = new ObjectMapper().readValue(statusResp.getRight(), Job.class);
              return jobStatus2.getStatusChanges().size() > 0
                  && jobStatus2.getStatus()
                      == com.datastax.mgmtapi.resources.models.Job.JobStatus.COMPLETED;
            });
  }
}
