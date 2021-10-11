/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.resources.*;
import com.datastax.mgmtapi.resources.models.*;
import com.datastax.mgmtapi.resources.models.CreateTableRequest.Column;
import com.datastax.mgmtapi.resources.models.CreateTableRequest.ColumnKind;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.jboss.resteasy.core.messagebody.WriterUtility;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Assert;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


public class K8OperatorResourcesTest {

    static String ROOT_PATH = "/api/v0";

    static class Context {

        Dispatcher dispatcher;
        CqlService cqlService;

        MockHttpResponse invoke(HttpRequest request) {
            MockHttpResponse response = new MockHttpResponse();
            dispatcher.invoke(request, response);
            return response;
        }
    }

    static Context setup() {
        Context context = new Context();
        context.dispatcher = createDispatcher();
        context.cqlService = mock(CqlService.class);

        ManagementApplication app = new ManagementApplication(null, null, null, context.cqlService,null);

        context.dispatcher.getRegistry()
                .addSingletonResource(new K8OperatorResources(app));
        context.dispatcher.getRegistry()
                .addSingletonResource(new KeyspaceOpsResources(app));
        context.dispatcher.getRegistry()
                .addSingletonResource(new MetadataResources(app));
        context.dispatcher.getRegistry()
                .addSingletonResource(new NodeOpsResources(app));
        context.dispatcher.getRegistry()
                .addSingletonResource(new TableOpsResources(app));

        return context;
    }

    private static Dispatcher createDispatcher() {
        return MockDispatcherFactory.createDispatcher();
    }

    @Test
    public void testLiveness() throws Exception {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/probes/liveness");

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));
    }

    @Test
    public void testSeedReload() throws Exception {
        Context context = setup();
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/seeds/reload");
        when(context.cqlService.executeCql(any(), anyString()))
                .thenReturn(mockResultSet);

        when(mockResultSet.one())
                .thenReturn(mockRow);

        when(mockRow.getList("result", String.class))
                .thenReturn(ImmutableList.of("127.0.0.1"));

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("[\"127.0.0.1\"]"));

        verify(context.cqlService).executeCql(any(), eq("CALL NodeOps.reloadSeeds()"));
    }

    @Test
    public void testDecommission() throws Exception {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/decommission?force=true");
        when(context.cqlService.executePreparedStatement(any(), anyString(), eq(true)))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.decommission(?)"), eq(true));
    }

    @Test
    public void testDecommissionMissingValue() throws Exception {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/decommission");
        when(context.cqlService.executePreparedStatement(any(), anyString(), eq(true)))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.decommission(?)"), eq(false));
    }

    @Test
    public void testSetCompactionThroughput() throws Exception {
        int value = 1;

        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/compaction?value=" + value);
        when(context.cqlService.executePreparedStatement(any(), anyString(), eq(true)))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.setCompactionThroughput(?)"), eq(value));
    }

    @Test
    public void testAssassinate() throws Exception {
        String address = "127.0.0.1";

        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/assassinate?address=" + address);
        when(context.cqlService.executePreparedStatement(any(), anyString(), eq(true)))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.assassinate(?)"), eq(address));
    }

    @Test
    public void testAssassinateMissingAddress() throws Exception {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/assassinate");
        when(context.cqlService.executePreparedStatement(any(), anyString(), eq(true)))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("Address must be provided"));

        verifyZeroInteractions(context.cqlService);
    }

    @Test
    public void testSetLoggingLevel() throws Exception {
        String classQualifier = "com.datastax.bdp.management.K8OperatorResourcesTest";
        String rawLevel = "info";

        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(String.format(ROOT_PATH + "/ops/node/logging?target=%s&rawLevel=%s", classQualifier, rawLevel));
        when(context.cqlService.executePreparedStatement(any(), anyString(), eq(true)))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.setLoggingLevel(?, ?)"), eq(classQualifier), eq(rawLevel));
    }

    @Test
    public void testSetLoggingLevel_MissingClassQualifier() throws Exception {
        String rawLevel = "info";

        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(String.format(ROOT_PATH + "/ops/node/logging?rawLevel=%s", rawLevel));
        when(context.cqlService.executePreparedStatement(any(), anyString(), eq(true)))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.setLoggingLevel(?, ?)"), eq(EMPTY), eq(rawLevel));
    }

    @Test
    public void testSetLoggingLevel_MissingRawLevel() throws Exception {
        String classQualifier = "com.datastax.bdp.management.K8OperatorResourcesTest";

        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(String.format(ROOT_PATH + "/ops/node/logging?target=%s", classQualifier));
        when(context.cqlService.executePreparedStatement(any(), anyString(), eq(true)))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.setLoggingLevel(?, ?)"), eq(classQualifier), eq(EMPTY));
    }

    @Test
    public void testDrain() throws Exception {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/drain");
        when(context.cqlService.executeCql(any(), anyString()))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executeCql(any(), eq("CALL NodeOps.drain()"));
    }

    @Test
    public void testTruncateHints_WithoutHost() throws Exception {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/hints/truncate");
        when(context.cqlService.executeCql(any(), anyString()))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executeCql(any(), eq("CALL NodeOps.truncateAllHints()"));
    }

    @Test
    public void testTruncateHints_WithHost() throws Exception {
        String host = "localhost";

        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/hints/truncate?host=" + host);
        when(context.cqlService.executePreparedStatement(any(), anyString(), eq(host)))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.truncateHintsForHost(?)"), eq(host));
    }

    @Test
    public void testResetLocalSchema() throws Exception {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/schema/reset");
        when(context.cqlService.executeCql(any(), anyString()))
                .thenReturn(null);

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executeCql(any(), eq("CALL NodeOps.resetLocalSchema()"));
    }

    @Test
    public void testReloadLocalSchema() throws Exception {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/schema/reload");
        when(context.cqlService.executeCql(any(), anyString()))
                .thenReturn(null);

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executeCql(any(), eq("CALL NodeOps.reloadLocalSchema()"));
    }

    @Test
    public void testScrub() throws Exception {
        ScrubRequest scrubRequest = new ScrubRequest(true, true, true, true,
                2, "keyspace", Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(scrubRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/scrub", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.scrub(?, ?, ?, ?, ?, ?, ?)"), any());
    }

    @Test
    public void testScrub_MissingTables() throws Exception {
        ScrubRequest scrubRequest = new ScrubRequest(true, true, true, true,
                2, "keyspace", null);


        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(scrubRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/scrub", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.scrub(?, ?, ?, ?, ?, ?, ?)"), any());
    }

    @Test
    public void testScrub_MissingKeyspace() throws Exception {
        ScrubRequest scrubRequest = new ScrubRequest(true, true, true, true,
                2, null, Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(scrubRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/scrub", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.scrub(?, ?, ?, ?, ?, ?, ?)"),
                any(), any(), any(), any(), any(), eq("ALL"), any());
    }

    @Test
    public void testUpgradeSSTables() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "keyspace", Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/sstables/upgrade", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.upgradeSSTables(?, ?, ?, ?)"), eq(keyspaceRequest.keyspaceName),
                eq(true), eq(keyspaceRequest.jobs), any());
    }

    @Test
    public void testUpgradeSSTables_MissingTables() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "keyspace", null);


        Context context = setup();

        when(context.cqlService.executeCql(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/sstables/upgrade", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.upgradeSSTables(?, ?, ?, ?)"), eq(keyspaceRequest.keyspaceName),
                eq(true), eq(keyspaceRequest.jobs), any());
    }

    @Test
    public void testUpgradeSSTables_MissingKeyspace() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, null, Arrays.asList("table1", "table2"));


        Context context = setup();

        when(context.cqlService.executeCql(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/sstables/upgrade", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.upgradeSSTables(?, ?, ?, ?)"), eq("ALL"),
                eq(true), eq(keyspaceRequest.jobs), any());
    }

    @Test
    public void testJobStatus() throws Exception {
        Context context = setup();

        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        when(context.cqlService.executePreparedStatement(any(), anyString(), anyString()))
                .thenReturn(mockResultSet);

        when(mockResultSet.one())
                .thenReturn(mockRow);

        Map<String, String> jobDetailsRow = new HashMap<>();
        jobDetailsRow.put("id", "0fe65b47-98c2-47d8-9c3c-5810c9988e10");
        jobDetailsRow.put("type", "CLEANUP");
        jobDetailsRow.put("status", "COMPLETED");
        jobDetailsRow.put("submit_time", String.valueOf(System.currentTimeMillis()));
        jobDetailsRow.put("end_time", String.valueOf(System.currentTimeMillis()));

        when(mockRow.getObject(0))
                .thenReturn(jobDetailsRow);

        MockHttpResponse response = getJobStatusWithId(context, "/ops/executor/job?job_id=0fe65b47-98c2-47d8-9c3c-5810c9988e10");

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.jobStatus(?)"), anyString());

        String json = response.getContentAsString();

        Job jobDetails = new ObjectMapper().readValue(json, Job.class);

        assertEquals("0fe65b47-98c2-47d8-9c3c-5810c9988e10", jobDetails.getJobId());
        assertEquals("COMPLETED", jobDetails.getStatus().toString());
        assertEquals("CLEANUP", jobDetails.getJobType());
    }

    private MockHttpResponse getJobStatusWithId(Context context, String path) throws URISyntaxException {
        MockHttpRequest request = MockHttpRequest
                .get(ROOT_PATH + path)
                .accept(MediaType.TEXT_PLAIN)
                .contentType(MediaType.APPLICATION_JSON_TYPE);

        return context.invoke(request);
    }

    @Test
    public void testCleanup() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "keyspace", Arrays.asList("table1", "table2"));

        Context context = setup();

        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        when(context.cqlService.executePreparedStatement(any(), any(), any()))
                .thenReturn(mockResultSet);

        when(mockResultSet.one())
                .thenReturn(mockRow);

        when(mockRow.getString(0))
                .thenReturn("0fe65b47-98c2-47d8-9c3c-5810c9988e10");

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/cleanup", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().length() > 0);

        verify(context.cqlService, timeout(500)).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceCleanup(?, ?, ?)"), any());
    }

    @Test
    public void testCleanup_SystemKeyspace() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "system", Arrays.asList("table1", "table2"));

        Context context = setup();

        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        when(context.cqlService.executePreparedStatement(any(), any(), any()))
                .thenReturn(mockResultSet);

        when(mockResultSet.one())
                .thenReturn(mockRow);

        when(mockRow.getString(0))
                .thenReturn("0fe65b47-98c2-47d8-9c3c-5810c9988e10");

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/cleanup", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().length() > 0);

        verifyZeroInteractions(context.cqlService);
    }

    @Test
    public void testCleanup_MissingTables() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "keyspace", null);

        Context context = setup();

        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        when(context.cqlService.executePreparedStatement(any(), any(), any()))
                .thenReturn(mockResultSet);

        when(mockResultSet.one())
                .thenReturn(mockRow);

        when(mockRow.getString(0))
                .thenReturn("0fe65b47-98c2-47d8-9c3c-5810c9988e10");

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/cleanup", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().length() > 0);

        verify(context.cqlService, timeout(500)).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceCleanup(?, ?, ?)"),
                any(), eq(keyspaceRequest.keyspaceName), any());
    }

    @Test
    public void testCleanup_MissingKeyspace() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, null, Arrays.asList("table1", "table2"));

        Context context = setup();

        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        when(context.cqlService.executePreparedStatement(any(), any(), any()))
                .thenReturn(mockResultSet);

        when(mockResultSet.one())
                .thenReturn(mockRow);

        when(mockRow.getString(0))
                .thenReturn("0fe65b47-98c2-47d8-9c3c-5810c9988e10");

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/cleanup", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().length() > 0);

        verify(context.cqlService, timeout(500)).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceCleanup(?, ?, ?)"),
                any(), eq("NON_LOCAL_STRATEGY"), any());
    }

    @Test
    public void testCompact() throws Exception {
        CompactRequest compactRequest = new CompactRequest(false, false, null,
                null, "keyspace", null, Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(compactRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/compact", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceCompaction(?, ?, ?)"),
                eq(compactRequest.splitOutput), eq(compactRequest.keyspaceName), any());
    }

    @Test
    public void testCompact_WithToken() throws Exception {
        CompactRequest compactRequest = new CompactRequest(false, false, "1",
                "2", "keyspace", null, Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(compactRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/compact", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceCompactionForTokenRange(?, ?, ?, ?)"),
                eq(compactRequest.keyspaceName), eq(compactRequest.startToken), eq(compactRequest.endToken), any());
    }

    @Test
    public void testCompact_UserDefined() throws Exception {
        CompactRequest compactRequest = new CompactRequest(false, true, null,
                null, "keyspace", Arrays.asList("file1", "file2"), null);

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(compactRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/compact", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceUserDefinedCompaction(?)"), any());
    }

    @Test
    public void testCompact_UserDefinedMissingFile() throws Exception {
        CompactRequest compactRequest = new CompactRequest(false, true, null,
                null, "keyspace", null, null);


        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(compactRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/compact", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("Must provide a file if setting userDefined to true"));

        verifyZeroInteractions(context.cqlService);
    }

    @Test
    public void testCompact_SplitOutputAndToken() throws Exception {
        CompactRequest compactRequest = new CompactRequest(true, true, "1",
                "2", "keyspace", null, Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(compactRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/compact", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("Invalid option combination: Can not use split-output here"));

        verifyZeroInteractions(context.cqlService);
    }

    @Test
    public void testCompact_UserDefinedAndToken() throws Exception {
        CompactRequest compactRequest = new CompactRequest(false, true, "1",
                "2", "keyspace", null, Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(compactRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/compact", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("Invalid option combination: Can not provide tokens when using user-defined"));

        verifyZeroInteractions(context.cqlService);
    }

    @Test
    public void testCompact_MissingTables() throws Exception {
        CompactRequest compactRequest = new CompactRequest(false, false, null,
                null, "keyspace", null,null);


        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(compactRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/compact", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceCompaction(?, ?, ?)"),
                eq(false), eq(compactRequest.keyspaceName), any());
    }

    @Test
    public void testCompact_MissingKeyspace() throws Exception {
        CompactRequest compactRequest = new CompactRequest(false, false, null,
                null, null, null, Arrays.asList("table1", "table2"));


        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(compactRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/compact", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceCompaction(?, ?, ?)"),
                eq(false), eq("ALL"), any());
    }

    @Test
    public void testGarbageCollect() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "keyspace", Arrays.asList("table1", "table2"));

        String tombstoneOption = "ROW";

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/garbagecollect?tombstoneOption=" + tombstoneOption, requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.garbageCollect(?, ?, ?, ?)"), eq(tombstoneOption),
                eq(1), eq(keyspaceRequest.keyspaceName), any());
    }

    @Test
    public void testGarbageCollect_MissingTables() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "keyspace", null);

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/garbagecollect", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.garbageCollect(?, ?, ?, ?)"), any(),
                eq(1), eq(keyspaceRequest.keyspaceName), any());
    }

    @Test
    public void testGarbageCollect_MissingKeyspace() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, null, Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/garbagecollect", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.garbageCollect(?, ?, ?, ?)"), any(),
                eq(1), eq("ALL"), any());
    }

    @Test
    public void testGarbageCollect_BadTombstoneOption() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "foo", Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/garbagecollect?tombstoneOption=foo", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("tombstoneOption must be either ROW or CELL"));

        verifyZeroInteractions(context.cqlService);
    }


    @Test
    public void testFlush() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "keyspace", Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/flush", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceFlush(?, ?)"),
                eq(keyspaceRequest.keyspaceName), any());
    }

    @Test
    public void testFlush_MissingTables() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "keyspace", null);


        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/flush", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceFlush(?, ?)"),
                eq(keyspaceRequest.keyspaceName), any());
    }

    @Test
    public void testFlush_MissingKeyspace() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, null, Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String requestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/tables/flush", requestAsJSON, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceFlush(?, ?)"),
                eq("ALL"), any());
    }

    @Test
    public void testRefresh() throws Exception {
        String keyspaceName = "keyspace";
        String table = "foo";
        String resetLevels = "true";

        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + String.format("/ops/keyspace/refresh?keyspaceName=%s&table=%s&resetLevels=%s", keyspaceName, table, resetLevels));
        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.loadNewSSTables(?, ?)"), eq(keyspaceName), eq(table));
    }

    @Test
    public void testRefresh_MissingTable() throws Exception {
        String keyspaceName = "keyspace";
        String resetLevels = "true";

        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + String.format("/ops/keyspace/refresh?keyspaceName=%s&resetLevels=%s", keyspaceName, resetLevels));
        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("table must be provided"));

        verifyZeroInteractions(context.cqlService);
    }

    @Test
    public void testRefresh_MissingKeyspace() throws Exception {
        String cfName = "foo";
        String resetLevels = "true";

        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + String.format("/ops/keyspace/refresh?cfName=%s&resetLevels=%s", cfName, resetLevels));
        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("Must provide a keyspace name"));

        verifyZeroInteractions(context.cqlService);
    }

    @Test
    public void testGetStreamInfo() throws Exception
    {
        Context context = setup();
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/ops/node/streaminfo");
        when(context.cqlService.executeCql(any(), anyString())).thenReturn(mockResultSet);

        when(mockResultSet.one()).thenReturn(mockRow);

        List<Map<String, List<Map<String, String>>>> result = new ArrayList<>();

        List<Map<String, String>> session1Results = new ArrayList<>();
        session1Results.add(ImmutableMap.<String, String>builder().put("PEER", "/10.101.35.153")
                .put("STREAM_OPERATION", "Rebuild")
                .put("TOTAL_FILES_RECEIVED", "0")
                .put("TOTAL_FILES_SENT", "6")
                .put("TOTAL_FILES_TO_RECEIVE", "0")
                .put("TOTAL_FILES_TO_SEND", "7")
                .put("TOTAL_SIZE_RECEIVED", "0")
                .put("TOTAL_SIZE_SENT", "104297862")
                .put("TOTAL_SIZE_TO_RECEIVE", "0")
                .put("TOTAL_SIZE_TO_SEND", "115157855")
                .put("USING_CONNECTION", "/10.101.35.153")
                .build());

        session1Results.add(ImmutableMap.<String, String>builder().put("PEER", "/10.101.35.123")
                .put("STREAM_OPERATION", "Rebuild")
                .put("TOTAL_FILES_RECEIVED", "3")
                .put("TOTAL_FILES_SENT", "6")
                .put("TOTAL_FILES_TO_RECEIVE", "9")
                .put("TOTAL_FILES_TO_SEND", "7")
                .put("TOTAL_SIZE_RECEIVED", "0")
                .put("TOTAL_SIZE_SENT", "104297862")
                .put("TOTAL_SIZE_TO_RECEIVE", "0")
                .put("TOTAL_SIZE_TO_SEND", "115157855")
                .put("USING_CONNECTION", "/10.101.35.123")
                .build());

        List<Map<String, String>> session2Results = new ArrayList<>();
        session2Results.add(ImmutableMap.<String, String>builder().put("PEER", "/10.101.35.111")
                .put("STREAM_OPERATION", "Repair")
                .put("TOTAL_FILES_RECEIVED", "0")
                .put("TOTAL_FILES_SENT", "6")
                .put("TOTAL_FILES_TO_RECEIVE", "0")
                .put("TOTAL_FILES_TO_SEND", "7")
                .put("TOTAL_SIZE_RECEIVED", "0")
                .put("TOTAL_SIZE_SENT", "104297862")
                .put("TOTAL_SIZE_TO_RECEIVE", "0")
                .put("TOTAL_SIZE_TO_SEND", "115157855")
                .put("USING_CONNECTION", "/10.101.35.111")
                .build());

        session2Results.add(ImmutableMap.<String, String>builder().put("PEER", "/10.101.35.222")
                .put("STREAM_OPERATION", "Repair")
                .put("TOTAL_FILES_RECEIVED", "3")
                .put("TOTAL_FILES_SENT", "6")
                .put("TOTAL_FILES_TO_RECEIVE", "9")
                .put("TOTAL_FILES_TO_SEND", "7")
                .put("TOTAL_SIZE_RECEIVED", "0")
                .put("TOTAL_SIZE_SENT", "104297862")
                .put("TOTAL_SIZE_TO_RECEIVE", "0")
                .put("TOTAL_SIZE_TO_SEND", "115157855")
                .put("USING_CONNECTION", "/10.101.35.222")
                .build());

        result.add(ImmutableMap.of("987-654-321-uuid", session1Results, "123-456-789-uuid", session2Results));

        String resultAsJSON = WriterUtility.asString(result, MediaType.APPLICATION_JSON);

        when(mockRow.getObject(0)).thenReturn(result);

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains(resultAsJSON));

        verify(context.cqlService).executeCql(any(), eq("CALL NodeOps.getStreamInfo()"));
    }

    @Test
    public void testCreatingKeyspace() throws IOException, URISyntaxException
    {
        CreateOrAlterKeyspaceRequest keyspaceRequest = new CreateOrAlterKeyspaceRequest("myKeyspace", Arrays.asList(new ReplicationSetting("dc1", 3), new ReplicationSetting("dc2", 3)));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/create", keyspaceRequestAsJSON, context);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        assertThat(response.getContentAsString()).contains("OK");

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.createKeyspace(?, ?)"), any());
    }

    @Test
    public void testCreatingEmptyKeyspaceShouldFail() throws IOException, URISyntaxException
    {
        CreateOrAlterKeyspaceRequest keyspaceRequest = new CreateOrAlterKeyspaceRequest("", Arrays.asList(new ReplicationSetting("dc1", 3), new ReplicationSetting("dc2", 3)));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/create", keyspaceRequestAsJSON, context);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("Keyspace creation failed. Non-empty 'keyspace_name' must be provided");
    }

    @Test
    public void testCreatingEmptyReplicationSettingsShouldFail() throws IOException, URISyntaxException
    {
        CreateOrAlterKeyspaceRequest keyspaceRequest = new CreateOrAlterKeyspaceRequest("TestKeyspace", Collections.emptyList());

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/create", keyspaceRequestAsJSON, context);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("Keyspace creation failed. 'replication_settings' must be provided");
    }

    @Test
    public void testAlteringKeyspace() throws IOException, URISyntaxException
    {
        CreateOrAlterKeyspaceRequest keyspaceRequest = new CreateOrAlterKeyspaceRequest("myKeyspace", Arrays.asList(new ReplicationSetting("dc1", 3), new ReplicationSetting("dc2", 3)));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/alter", keyspaceRequestAsJSON, context);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        assertThat(response.getContentAsString()).contains("OK");

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.alterKeyspace(?, ?)"), any());
    }

    @Test
    public void testAlteringEmptyKeyspaceShouldFail() throws IOException, URISyntaxException
    {
        CreateOrAlterKeyspaceRequest keyspaceRequest = new CreateOrAlterKeyspaceRequest("", Arrays.asList(new ReplicationSetting("dc1", 3), new ReplicationSetting("dc2", 3)));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/alter", keyspaceRequestAsJSON, context);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("Altering Keyspace failed. Non-empty 'keyspace_name' must be provided");
    }

    @Test
    public void testAlteringEmptyReplicationSettingsShouldFail() throws IOException, URISyntaxException
    {
        CreateOrAlterKeyspaceRequest keyspaceRequest = new CreateOrAlterKeyspaceRequest("TestKeyspace", Collections.emptyList());

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/alter", keyspaceRequestAsJSON, context);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("Altering Keyspace failed. 'replication_settings' must be provided");
    }

    private MockHttpResponse postWithBody(String path, String body, Context context) throws URISyntaxException {
        MockHttpRequest request = MockHttpRequest
                .post(ROOT_PATH + path)
                .content(body.getBytes())
                .accept(MediaType.TEXT_PLAIN)
                .contentType(MediaType.APPLICATION_JSON_TYPE);

        return context.invoke(request);
    }

    @Test
    public void testTakeSnapshotWithKeyspaceAndKeyspaceTablesShouldFail() throws Exception
    {
        TakeSnapshotRequest takeSnapshotRequest = new TakeSnapshotRequest("testSnapshot", Arrays.asList("testKeyspace"), null, null, Arrays.asList("testKeyspace.testTable"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String takeSnapshotRequestAsJSON = WriterUtility.asString(takeSnapshotRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/node/snapshots", takeSnapshotRequestAsJSON, context);
        
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).isEqualTo("When specifying keyspace_tables, specifying keyspaces is not allowed");
    }

    @Test
    public void testTakeSnapshotWithTableNameAndNoKeyspaceShouldFail() throws Exception
    {
        TakeSnapshotRequest takeSnapshotRequest = new TakeSnapshotRequest("testSnapshot", null, "testTable", null, null);

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String takeSnapshotRequestAsJSON = WriterUtility.asString(takeSnapshotRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/node/snapshots", takeSnapshotRequestAsJSON, context);
        
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).isEqualTo("Exactly 1 keyspace must be specified when specifying table_name");
    }

    @Test
    public void testTakeSnapshotWithTableNameAndMultipleKeyspacesShouldFail() throws Exception
    {
        TakeSnapshotRequest takeSnapshotRequest = new TakeSnapshotRequest("testSnapshot", Arrays.asList("test_ks1", "test_ks2"), "testTable", null, null);

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String takeSnapshotRequestAsJSON = WriterUtility.asString(takeSnapshotRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/node/snapshots", takeSnapshotRequestAsJSON, context);
        
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).isEqualTo("Exactly 1 keyspace must be specified when specifying table_name");
    }

    @Test
    public void testTakeSnapshot() throws Exception
    {
        TakeSnapshotRequest takeSnapshotRequest = new TakeSnapshotRequest(null, null, null, null, null);

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String takeSnapshotRequestAsJSON = WriterUtility.asString(takeSnapshotRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/node/snapshots", takeSnapshotRequestAsJSON, context);
        
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.takeSnapshot(?, ?, ?, ?, ?)"), any());
    }

    @Test
    public void testGetSnapshotDetails() throws Exception
    {
        Context context = setup();
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/ops/node/snapshots");
        when(context.cqlService.executePreparedStatement(any(), anyString(), any())).thenReturn(mockResultSet);

        when(mockResultSet.one()).thenReturn(mockRow);

        List<Map<String, String>> result = new ArrayList<>();
        
        Map<String, String> result1 = ImmutableMap.<String, String>builder()
                .put("Column family name","events")
                .put("Keyspace name","system_traces")
                .put("Size on disk","13 bytes")
                .put("Snapshot name","my_snapshot")
                .put("True size","0 bytes")
                .build();

        Map<String, String> result2 = ImmutableMap.<String, String>builder()
                .put("Column family name","table1")
                .put("Keyspace name","test_ks")
                .put("Size on disk","5.62 KiB")
                .put("Snapshot name","my_snapshot")
                .put("True size","4.78 KiB")
                .build();

        result.addAll(Arrays.asList(result1, result2));

        String resultAsJSON = WriterUtility.asString(result, MediaType.APPLICATION_JSON);

        when(mockRow.getObject(0)).thenReturn(result);

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains(resultAsJSON));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.getSnapshotDetails(?, ?)"), any());
    }

    @Test
    public void testDeleteSnapshotDetails() throws Exception
    {
        Context context = setup();

        MockHttpRequest request = MockHttpRequest.delete(ROOT_PATH + "/ops/node/snapshots");
        when(context.cqlService.executePreparedStatement(any(), anyString(), any())).thenReturn(null);

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.clearSnapshots(?, ?)"), any());
    }

    @Test
    public void testGetKeyspaces() throws Exception
    {
        Context context = setup();
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/ops/keyspace");
        when(context.cqlService.executePreparedStatement(any(), anyString())).thenReturn(mockResultSet);
        when(mockResultSet.one()).thenReturn(mockRow);
        List<String> result = Arrays.asList("system_auth", "system", "system_distributed");
        String resultAsJSON = WriterUtility.asString(result, MediaType.APPLICATION_JSON);
        when(mockRow.getList(0, String.class)).thenReturn(result);

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains(resultAsJSON));
        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.getKeyspaces()"));
    }

    @Test
    public void testGetKeyspacesWithFilter() throws Exception
    {
        Context context = setup();
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/ops/keyspace?keyspaceName=system");
        when(context.cqlService.executePreparedStatement(any(), anyString())).thenReturn(mockResultSet);
        when(mockResultSet.one()).thenReturn(mockRow);
        List<String> result = Arrays.asList("system_auth", "system", "system_distributed");
        List<String> filteredResult = Arrays.asList("system");
        String filteredResultAsJSON = WriterUtility.asString(filteredResult, MediaType.APPLICATION_JSON);
        when(mockRow.getList(0, String.class)).thenReturn(result);

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains(filteredResultAsJSON));
        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.getKeyspaces()"), any());
    }

    @Test
    public void testRepair() throws Exception
    {
        Context context = setup();
        when(context.cqlService.executePreparedStatement(any(), anyString())).thenReturn(null);

        RepairRequest repairRequest = new RepairRequest("test_ks", null, Boolean.TRUE);
        String repairRequestAsJSON = WriterUtility.asString(repairRequest, MediaType.APPLICATION_JSON);

        MockHttpResponse response = postWithBody("/ops/node/repair", repairRequestAsJSON, context);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.repair(?, ?, ?)"), eq("test_ks"), eq(null), eq(true));
    }

    @Test
    public void testRepairRequiresKeyspaceName() throws Exception
    {
        Context context = setup();
        when(context.cqlService.executePreparedStatement(any(), anyString())).thenReturn(null);

        RepairRequest repairRequest = new RepairRequest(null, null, Boolean.TRUE);
        String repairRequestAsJSON = WriterUtility.asString(repairRequest, MediaType.APPLICATION_JSON);

        MockHttpResponse response = postWithBody("/ops/node/repair", repairRequestAsJSON, context);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).isEqualTo("keyspaceName must be specified");
    }

    @Test
    public void testGetReplication() throws Exception
    {
        Context context = setup();
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/ops/keyspace/replication?keyspaceName=ks1");
        when(context.cqlService.executePreparedStatement(any(), anyString(), anyString())).thenReturn(mockResultSet);
        when(mockResultSet.one()).thenReturn(mockRow);
        Map<String, String> result = ImmutableMap.of("class", "org.apache.cassandra.locator.NetworkTopologyStrategy", "dc1", "3", "dc2", "1");
        when(mockRow.getMap(0, String.class, String.class)).thenReturn(result);

        MockHttpResponse response = context.invoke(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        Map<String, String> actual = new JsonMapper().readValue(response.getContentAsString(), new TypeReference<Map<String, String>>()
        {
        });
        assertThat(actual).containsAllEntriesOf(result);
        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.getReplication(?)"), eq("ks1"));
    }

    @Test
    public void testGetReplicationRequiresKeyspaceName() throws Exception
    {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/ops/keyspace/replication");
        MockHttpResponse response = context.invoke(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("Non-empty 'keyspaceName' must be provided");
    }

    @Test
    public void testGetReplicationKeyspaceDoesNotExist() throws Exception
    {
        Context context = setup();
        ResultSet mockResultSet = mock(ResultSet.class);

        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/ops/keyspace/replication?keyspaceName=ks1");
        when(context.cqlService.executePreparedStatement(any(), anyString(), anyString())).thenReturn(mockResultSet);
        when(mockResultSet.one()).thenReturn(null);

        MockHttpResponse response = context.invoke(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NOT_FOUND);
        assertThat(response.getContentAsString()).contains("Keyspace 'ks1' does not exist");
    }

    @Test
    public void testListTables() throws Exception
    {
        Context context = setup();
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/ops/tables?keyspaceName=ks1");
        when(context.cqlService.executePreparedStatement(any(), anyString(), anyString())).thenReturn(mockResultSet);
        when(mockResultSet.one()).thenReturn(mockRow);
        List<String> result = ImmutableList.of("table1", "table2");
        when(mockRow.getList(0, String.class)).thenReturn(result);

        MockHttpResponse response = context.invoke(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        String[] actual = new JsonMapper().readValue(response.getContentAsString(), String[].class);
        assertThat(actual).containsExactlyElementsOf(result);
        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.getTables(?)"), eq("ks1"));
    }

    @Test
    public void testListTablesRequiresKeyspaceName() throws Exception
    {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/ops/tables");
        MockHttpResponse response = context.invoke(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("Non-empty 'keyspaceName' must be provided");
        verify(context.cqlService, never()).executePreparedStatement(any(), eq("CALL NodeOps.getTables(?)"), eq("ks1"));
    }

    @Test
    public void testListTablesKeyspaceDoesNotExist() throws Exception
    {
        Context context = setup();
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/ops/tables?keyspaceName=ks1");
        when(context.cqlService.executePreparedStatement(any(), anyString(), anyString())).thenReturn(mockResultSet);
        when(mockResultSet.one()).thenReturn(mockRow);
        when(mockRow.getList(0, String.class)).thenReturn(Collections.emptyList());

        MockHttpResponse response = context.invoke(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        assertThat(response.getContentAsString()).isEqualTo("[]");
        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.getTables(?)"), eq("ks1"));
    }

    @Test
    public void testCreateTable() throws Exception
    {
        Context context = setup();

        List<Column> columns = ImmutableList.of(
            new Column("pk1", "int", ColumnKind.PARTITION_KEY, 0, null),
            new Column("pk2", "int", ColumnKind.PARTITION_KEY, 1, null),
            new Column("cc1", "timeuuid", ColumnKind.CLUSTERING_COLUMN, 0, ClusteringOrder.ASC),
            new Column("cc2", "timeuuid", ColumnKind.CLUSTERING_COLUMN, 1, ClusteringOrder.DESC),
            new Column("v", "frozen<list<tuple<int,map<string,string>,udt>>>", ColumnKind.REGULAR, 0, null),
            new Column("s", "udt2", ColumnKind.STATIC, 0, null)
        );

        Map<String, Object> options = ImmutableMap.of(
            "option1", "value1",
            "option2", ImmutableMap.of(
                "option2a", "value2a",
                "option2b", "value2b"));

        CreateTableRequest body = new CreateTableRequest("ks1", "table1", columns, options);

        when(context.cqlService
            .executePreparedStatement(
                any(),
                eq("CALL NodeOps.createTable(?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                anyString(), // keyspace name
                anyString(), // table name
                anyMap(),    // columns and types
                anyList(),   // partition key
                anyList(),   // clustering columns
                anyMap(),    // orderings
                anyList(),   // static columns
                anyMap(),    // simple options
                anyMap()     // complex options
        )).thenReturn(null);

        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/tables/create")
                                                 .content(new JsonMapper().writeValueAsBytes(body))
                                                 .accept(MediaType.TEXT_PLAIN)
                                                 .contentType(MediaType.APPLICATION_JSON_TYPE);

        MockHttpResponse response = context.invoke(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        verify(context.cqlService).executePreparedStatement(
            any(),
            eq("CALL NodeOps.createTable(?, ?, ?, ?, ?, ?, ?, ?, ?)"),
            eq("ks1"),
            eq("table1"),
            eq(ImmutableMap.builder()
               .put("pk1", "int")
               .put("pk2", "int")
               .put("cc1", "timeuuid")
               .put("cc2", "timeuuid")
               .put("v", "frozen<list<tuple<int,map<string,string>,udt>>>")
               .put("s", "udt2")
               .build()),
            eq(ImmutableList.of("pk1", "pk2")),
            eq(ImmutableList.of("cc1", "cc2")),
            eq(ImmutableList.of("ASC", "DESC")),
            eq(ImmutableList.of("s")),
            eq(ImmutableMap.of("option1", "value1")),
            eq(ImmutableMap.of("option2", ImmutableMap.of("option2a", "value2a", "option2b", "value2b")))
        );
    }

    @Test
    public void testCreateTableInvalid() throws Exception
    {
        Context context = setup();

        List<Pair<CreateTableRequest, String>> testCases = ImmutableList.of(
            Pair.of(new CreateTableRequest("", "table1", ImmutableList.of(), null),
                 "Table creation failed: 'keyspace_name' must not be empty"
            ),
            Pair.of(new CreateTableRequest("ks1", "", ImmutableList.of(), null),
                 "Table creation failed: 'table_name' must not be empty"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(), null),
                 "Table creation failed: 'columns' must not be empty"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", ColumnKind.PARTITION_KEY, 0, null),
                 new Column("pk", "int", ColumnKind.REGULAR, 0, null)), null),
                 "Table creation failed: duplicated column name: 'pk'"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("", "int", ColumnKind.PARTITION_KEY, 0, null)), null),
                 "Table creation failed: 'columns[0].name' must not be empty"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "", ColumnKind.PARTITION_KEY, 0, null)), null),
                 "Table creation failed: 'columns[0].type' must not be empty"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "list<", ColumnKind.PARTITION_KEY, 0, null)), null),
                 "Table creation failed: 'columns[0].type' is invalid: Syntax error parsing 'list<' at char 5: unexpected end of string"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", null, 0, null)), null),
                 "Table creation failed: 'columns[0].kind' must not be null"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", ColumnKind.PARTITION_KEY, -1, null)), null),
                 "Table creation failed: 'columns[0].position' must not be negative for partition key columns"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", ColumnKind.PARTITION_KEY, 0, null),
                 new Column("cc", "int", ColumnKind.CLUSTERING_COLUMN, -1, null)), null),
                 "Table creation failed: 'columns[1].position' must not be negative for clustering columns"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", ColumnKind.PARTITION_KEY, 0, null),
                 new Column("cc", "int", ColumnKind.CLUSTERING_COLUMN, 0, null)), null),
                 "Table creation failed: 'columns[1].order' must not be empty for clustering columns"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", ColumnKind.REGULAR, 0, null)), null),
                 "Table creation failed: invalid primary key: partition key is empty"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", ColumnKind.PARTITION_KEY, 1, null)), null),
                 "Table creation failed: invalid primary key: missing partition key at position 0"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk1", "int", ColumnKind.PARTITION_KEY, 0, null),
                 new Column("pk2", "int", ColumnKind.PARTITION_KEY, 0, null)), null),
                 "Table creation failed: invalid primary key: found 2 partition key columns at position 0"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", ColumnKind.PARTITION_KEY, 0, null),
                 new Column("cc", "int", ColumnKind.CLUSTERING_COLUMN, 1, ClusteringOrder.ASC)), null),
                 "Table creation failed: invalid primary key: missing clustering column at position 0"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", ColumnKind.PARTITION_KEY, 0, null),
                 new Column("cc1", "int", ColumnKind.CLUSTERING_COLUMN, 0, ClusteringOrder.ASC),
                 new Column("cc2", "int", ColumnKind.CLUSTERING_COLUMN, 0, ClusteringOrder.ASC)), null),
                 "Table creation failed: invalid primary key: found 2 clustering columns at position 0"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", ColumnKind.PARTITION_KEY, 0, null)),
                                           new LinkedHashMap<String, Object>()
                       {{
                           put("option1", null);
                       }}),
                 "Table creation failed: invalid value for option 'option1': expected String or Map<String,String>, got: null"
            ),
            Pair.of(new CreateTableRequest("ks1", "table1", ImmutableList.of(
                 new Column("pk", "int", ColumnKind.PARTITION_KEY, 0, null)),
                                           ImmutableMap.of("option1", ImmutableMap.of("option1a", "value1a", "option1b", 123))),
                 "Table creation failed: invalid value for option 'option1': expected String or Map<String,String>, got: {option1a=value1a, option1b=123}"
            )
        );

        JsonMapper jsonMapper = new JsonMapper();

        for (Pair<CreateTableRequest, String> testCase : testCases)
        {
            String testCaseDescription = jsonMapper.writeValueAsString(testCase.getKey());
            MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/tables/create")
                                                     .content(testCaseDescription.getBytes())
                                                     .accept(MediaType.TEXT_PLAIN)
                                                     .contentType(MediaType.APPLICATION_JSON_TYPE);
            MockHttpResponse response = context.invoke(request);
            assertThat(response.getStatus())
                .describedAs(testCaseDescription)
                .isEqualTo(HttpStatus.SC_BAD_REQUEST);
            assertThat(response.getContentAsString())
                .describedAs(testCaseDescription)
                .isEqualTo(testCase.getValue());
        }
    }
}
