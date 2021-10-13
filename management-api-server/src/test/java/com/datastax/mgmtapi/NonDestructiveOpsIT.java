/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;

import com.datastax.mgmtapi.resources.models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.resources.models.CreateTableRequest.Column;
import com.datastax.mgmtapi.resources.models.CreateTableRequest.ColumnKind;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import io.netty.handler.codec.http.FullHttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.jboss.resteasy.core.messagebody.ReaderUtility;
import org.jboss.resteasy.core.messagebody.WriterUtility;

import static io.netty.util.CharsetUtil.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Class for integration testing non-destructive actions. By non-destructive this means actions that do not leave a node
 * in an inoperable state (e.g. assassinate or decommission). The purpose of this is to speed up testing by starting the Cassandra
 * node once, running all tests, and then stopping rather than a start/stop during each test case.
 */
@RunWith(Parameterized.class)
public class NonDestructiveOpsIT extends BaseDockerIntegrationTest
{
    private static final Logger logger = LoggerFactory.getLogger(NonDestructiveOpsIT.class);


    public NonDestructiveOpsIT(String version) throws IOException
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
                    return responseAsString(r);
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

        // try IP of container
        URI uri = new URIBuilder(BASE_PATH + "/ops/node/hints/truncate")
                  .addParameter("host", docker.getIpAddressOfContainer())
                  .build();
        boolean requestSuccessful = client.post(uri.toURL(), null)
                                          .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

        if (!requestSuccessful)
        {
            // try 127.0.0.1
            uri = new URIBuilder(BASE_PATH + "/ops/node/hints/truncate")
                  .addParameter("host", "127.0.0.1")
                  .build();
            requestSuccessful = client.post(uri.toURL(), null)
                                      .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
            assertTrue(requestSuccessful);
        }
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
                    return responseAsString(r);
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
                    return responseAsString(r);
                }).join();

        System.err.println(response);
        assertNotNull(response);
        assertNotEquals("", response);
    }

    @Test
    public void testCleanup() throws IOException, URISyntaxException, InterruptedException {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "system_traces", Collections.singletonList("events"));
        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace/cleanup")
                .build();

        // Get job_id here..
        Pair<Integer, String> postResponse = client.post(uri.toURL(), keyspaceRequestAsJSON)
                .thenApply(this::responseAsCodeAndBody)
                .join();
        assertEquals(HttpStatus.SC_OK, postResponse.getLeft().longValue());

        String jobId = postResponse.getRight();
        assertNotNull(jobId); // If return code != OK, this is null

        // Add here the check for the job and that is actually is set to complete..
        Job currentStatus = null;
        for(int i = 0; i < 10; i++) {
            URI uriJobStatus = new URIBuilder(BASE_PATH + "/ops/executor/job?job_id=" + jobId)
                    .build();
             currentStatus = client.get(uriJobStatus.toURL())
                    .thenApply(re -> {
                        String jobJson = responseAsString(re);
                        try {
                            return new ObjectMapper().readValue(jobJson, Job.class);
                        } catch (JsonProcessingException e) {
                            fail();
                        }
                        return null;
                    }).join();
            if(currentStatus != null) {
                if(currentStatus.getStatus() == Job.JobStatus.COMPLETED) {
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
                .thenApply(this::responseAsString).join();
        assertNotNull(response);
        assertNotEquals("", response);
    }

    @Test
    public void testCreateKeyspace() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);
        String localDc = client.get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
                .thenApply(this::responseAsString).join();

        createKeyspace(client, localDc, "someTestKeyspace");
    }

    @Test
    public void testAlterKeyspace() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);
        String localDc = client.get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
                .thenApply(this::responseAsString).join();

        String ks = "alteringKeyspaceTest";
        createKeyspace(client, localDc, ks);

        CreateOrAlterKeyspaceRequest request = new CreateOrAlterKeyspaceRequest(ks, Arrays.asList(new ReplicationSetting(localDc, 3)));
        String requestAsJSON = WriterUtility.asString(request, MediaType.APPLICATION_JSON);

        boolean requestSuccessful = client.post(new URIBuilder(BASE_PATH + "/ops/keyspace/alter").build().toURL(), requestAsJSON)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    @Test
    public void testGetKeyspaces() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);
        String localDc = client.get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
                .thenApply(this::responseAsString).join();

        String ks = "getkeyspacestest";
        createKeyspace(client, localDc, ks);


        URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace").build();
        String response = client.get(uri.toURL())
                .thenApply(this::responseAsString).join();
        assertNotNull(response);
        assertNotEquals("", response);
        assertTrue(response.contains(ks));

        URI uriFilter = new URIBuilder(BASE_PATH + "/ops/keyspace?keyspaceName=" + ks).build();
        String responseFilter = client.get(uriFilter.toURL())
                .thenApply(this::responseAsString).join();
        assertNotNull(responseFilter);
        assertNotEquals("", responseFilter);

        final ObjectMapper jsonMapper = new ObjectMapper();
        List<String> keyspaces = jsonMapper.readValue(responseFilter, new TypeReference<List<String>>(){});
        assertEquals(1, keyspaces.size());
        assertEquals(ks, keyspaces.get(0));
    }

    @Test
    public void testGetSnapshotDetails() throws IOException, URISyntaxException, InterruptedException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URIBuilder uriBuilder = new URIBuilder(BASE_PATH + "/ops/node/snapshots");
        URI takeSnapshotUri = uriBuilder.build();

        // create a snapshot
        TakeSnapshotRequest takeSnapshotRequest = new TakeSnapshotRequest("testSnapshot",  Arrays.asList("system_schema", "system_traces", "system_distributed"), null, null, null);
        String requestAsJSON = WriterUtility.asString(takeSnapshotRequest, MediaType.APPLICATION_JSON);

        boolean takeSnapshotSuccessful = client.post(takeSnapshotUri.toURL(), requestAsJSON).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(takeSnapshotSuccessful);

        // get snapshot details
        URI getSnapshotsUri = uriBuilder.addParameter("snapshotNames", "testSnapshot").build();
        String getSnapshotResponse = client.get(getSnapshotsUri.toURL())
                .thenApply(this::responseAsString).join();
        assertNotNull(getSnapshotResponse);
        Object responseObject = ReaderUtility.read(Object.class, MediaType.APPLICATION_JSON, getSnapshotResponse);
        assertTrue(responseObject instanceof Map);
        Map<Object, Object> responseObj = (Map)responseObject;
        assertTrue(responseObj.containsKey("entity"));
        Object entityObj = responseObj.get("entity");
        assertTrue(entityObj instanceof List);
        List<Object> entities = (List<Object>)entityObj;
        assertFalse(entities.isEmpty());
        for (Object entity : entities)
        {
            assertTrue(entity instanceof Map);
            Map<String, String> entityMap = (Map<String, String>)entity;
            assertTrue(entityMap.containsKey("Snapshot name"));
            String snapshotName = entityMap.get("Snapshot name");
            assertEquals("testSnapshot", snapshotName);
        }

        // delete snapshot
        URI clearSnapshotsUri = uriBuilder.addParameter("snapshotNames", "testSnapshot").build();
        boolean clearSnapshotSuccessful = client.delete(clearSnapshotsUri.toURL()).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(clearSnapshotSuccessful);

        // verify snapshot deleted
        getSnapshotResponse = client.get(getSnapshotsUri.toURL())
                .thenApply(this::responseAsString).join();
        assertNotNull(getSnapshotResponse);
        responseObject = ReaderUtility.read(Object.class, MediaType.APPLICATION_JSON, getSnapshotResponse);
        assertTrue(responseObject instanceof Map);
        responseObj = (Map)responseObject;
        assertTrue(responseObj.containsKey("entity"));
        entityObj = responseObj.get("entity");
        assertTrue(entityObj instanceof List);
        entities = (List<Object>)entityObj;
        assertTrue(entities.isEmpty());
    }

    @Test
    public void testRepair() throws IOException, URISyntaxException, InterruptedException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        URIBuilder uriBuilder = new URIBuilder(BASE_PATH + "/ops/node/repair");
        URI repairUri = uriBuilder.build();

        // execute repair
        RepairRequest repairRequest = new RepairRequest("system_auth", null, Boolean.TRUE);
        String requestAsJSON = WriterUtility.asString(repairRequest, MediaType.APPLICATION_JSON);

        boolean repairSuccessful = client.post(repairUri.toURL(), requestAsJSON).thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue("Repair request was not successful", repairSuccessful);
    }

    @Test
    public void testGetReplication() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);
        String localDc = client.get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
                .thenApply(this::responseAsString).join();

        String ks = "getreplicationtest";
        createKeyspace(client, localDc, ks);

        // missing keyspace
        URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace/replication").build();
        Pair<Integer, String> response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
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

        Map<String, String> actual = new JsonMapper().readValue(response.getRight(), new TypeReference<Map<String,String>>(){});
        assertThat(actual).hasSize(2)
                .containsEntry("class", "org.apache.cassandra.locator.NetworkTopologyStrategy")
                .containsKey(localDc);
    }

    @Test
    public void testGetTables() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);

        // missing keyspace
        URI uri = new URIBuilder(BASE_PATH + "/ops/tables").build();
        Pair<Integer, String> response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
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

        List<String> actual = new JsonMapper().readValue(response.getRight(), new TypeReference<List<String>>(){});
        assertThat(actual).contains(
            "aggregates",
            "columns",
            "functions",
            "indexes",
            "keyspaces",
            "tables",
            "triggers",
            "types",
            "views"
        );
    }

    @Test
    public void testCreateTable() throws IOException, URISyntaxException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());
        ensureStarted();

        NettyHttpClient client = new NettyHttpClient(BASE_URL);
        String localDc = client.get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
                .thenApply(this::responseAsString).join();

        // this test also tests case sensitivity in CQL identifiers.
        String ks = "CreateTableTest";
        createKeyspace(client, localDc, ks);

        CreateTableRequest request = new CreateTableRequest(
            ks,
            "Table1",
            ImmutableList.of(
                // having two columns with the same name in different cases can only work if the internal name is being used.
                new Column("pk", "int", ColumnKind.PARTITION_KEY, 0, null),
                new Column("PK", "int", ColumnKind.PARTITION_KEY, 1, null),
                new Column("cc", "timeuuid", ColumnKind.CLUSTERING_COLUMN, 0, ClusteringOrder.ASC),
                new Column("CC", "timeuuid", ColumnKind.CLUSTERING_COLUMN, 1, ClusteringOrder.DESC),
                new Column("v", "list<text>", ColumnKind.REGULAR, 0, null),
                new Column("s", "boolean", ColumnKind.STATIC, 0, null)
            ),
            ImmutableMap.of(
                "bloom_filter_fp_chance", "0.01",
                "caching", ImmutableMap.of( "keys", "ALL", "rows_per_partition" , "NONE" )
            ));

        JsonMapper jsonMapper = new JsonMapper();

        URI uri = new URIBuilder(BASE_PATH + "/ops/tables/create").build();
        Pair<Integer, String> response = client.post(uri.toURL(), jsonMapper.writeValueAsString(request))
                                               .thenApply(this::responseAsCodeAndBody).join();
        assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);

        uri = new URIBuilder(BASE_PATH + "/ops/tables?keyspaceName=" + ks).build();
        response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
        assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);

        List<String> actual = jsonMapper.readValue(response.getRight(), new TypeReference<List<String>>(){});
        assertThat(actual).containsExactly("Table1");
    }

    private void createKeyspace(NettyHttpClient client, String localDc, String keyspaceName) throws IOException, URISyntaxException
    {
        CreateOrAlterKeyspaceRequest request = new CreateOrAlterKeyspaceRequest(keyspaceName, Arrays.asList(new ReplicationSetting(localDc, 1)));
        String requestAsJSON = WriterUtility.asString(request, MediaType.APPLICATION_JSON);

        URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace/create")
                .build();
        boolean requestSuccessful = client.post(uri.toURL(), requestAsJSON)
                .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();
        assertTrue(requestSuccessful);
    }

    private String responseAsString(FullHttpResponse r)
    {
        if (r.status().code() == HttpStatus.SC_OK)
        {
            byte[] result = new byte[r.content().readableBytes()];
            r.content().readBytes(result);

            return new String(result);
        }

        return null;
    }

    private Pair<Integer, String> responseAsCodeAndBody(FullHttpResponse r)
    {
        return Pair.of(r.status().code(), r.content().toString(UTF_8));
    }
}
