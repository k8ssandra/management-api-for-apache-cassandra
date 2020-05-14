/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.datastax.mgmtapi.resources.K8OperatorResources;
import com.datastax.mgmtapi.resources.KeyspaceOpsResources;
import com.datastax.mgmtapi.resources.MetadataResources;
import com.datastax.mgmtapi.resources.NodeOpsResources;
import com.datastax.mgmtapi.resources.TableOpsResources;
import com.datastax.mgmtapi.resources.models.CompactRequest;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import com.datastax.mgmtapi.resources.models.ScrubRequest;
import org.jboss.resteasy.core.messagebody.WriterUtility;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Assert;
import org.junit.Test;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


public class K8OperatorResourcesTest {

    String ROOT_PATH = "/api/v0";

    static class Context {

        Dispatcher dispatcher;
        CqlService cqlService;

        MockHttpResponse invoke(HttpRequest request) {
            MockHttpResponse response = new MockHttpResponse();
            dispatcher.invoke(request, response);
            return response;
        }
    }

    private static Context setup() {
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("[\"127.0.0.1\"]"));

        verify(context.cqlService).executeCql(any(), eq("CALL NodeOps.reloadSeeds()"));
    }

    @Test
    public void testGetReleaseVersion() throws Exception {
        Context context = setup();
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        MockHttpRequest request = MockHttpRequest.get(ROOT_PATH + "/metadata/versions/release");
        when(context.cqlService.executeCql(any(), anyString()))
                .thenReturn(mockResultSet);

        when(mockResultSet.one())
                .thenReturn(mockRow);

        when(mockRow.getString(0))
                .thenReturn("1.2.3");

        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("1.2.3"));

        verify(context.cqlService).executeCql(any(), eq("CALL NodeOps.getReleaseVersion()"));
    }

    @Test
    public void testDecommission() throws Exception {
        Context context = setup();
        MockHttpRequest request = MockHttpRequest.post(ROOT_PATH + "/ops/node/decommission?force=true");
        when(context.cqlService.executePreparedStatement(any(), anyString(), eq(true)))
                .thenReturn(null);


        MockHttpResponse response = context.invoke(request);

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.upgradeSSTables(?, ?, ?, ?)"), eq("ALL"),
                eq(true), eq(keyspaceRequest.jobs), any());
    }

    @Test
    public void testCleanup() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "keyspace", Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/cleanup", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceCleanup(?, ?, ?)"), any());
    }

    @Test
    public void testCleanup_SystemKeyspace() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "system", Arrays.asList("table1", "table2"));


        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/cleanup", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verifyZeroInteractions(context.cqlService);
    }

    @Test
    public void testCleanup_MissingTables() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, "keyspace", null);

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/cleanup", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceCleanup(?, ?, ?)"),
                any(), eq(keyspaceRequest.keyspaceName), any());
    }

    @Test
    public void testCleanup_MissingKeyspace() throws Exception {
        KeyspaceRequest keyspaceRequest = new KeyspaceRequest(1, null, Arrays.asList("table1", "table2"));

        Context context = setup();

        when(context.cqlService.executePreparedStatement(any(), anyString()))
                .thenReturn(null);

        String keyspaceRequestAsJSON = WriterUtility.asString(keyspaceRequest, MediaType.APPLICATION_JSON);
        MockHttpResponse response = postWithBody("/ops/keyspace/cleanup", keyspaceRequestAsJSON, context);

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("OK"));

        verify(context.cqlService).executePreparedStatement(any(), eq("CALL NodeOps.forceKeyspaceCleanup(?, ?, ?)"),
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
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

        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains(resultAsJSON));

        verify(context.cqlService).executeCql(any(), eq("CALL NodeOps.getStreamInfo()"));
    }

    private MockHttpResponse postWithBody(String path, String body, Context context) throws URISyntaxException {
        MockHttpRequest request = MockHttpRequest
                .post(ROOT_PATH + path)
                .content(body.getBytes())
                .accept(MediaType.TEXT_PLAIN)
                .contentType(MediaType.APPLICATION_JSON_TYPE);

        return context.invoke(request);
    }
}
