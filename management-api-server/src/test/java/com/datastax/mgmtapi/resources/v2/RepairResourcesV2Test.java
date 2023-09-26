/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v2;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.v2.models.RepairParallelism;
import com.datastax.mgmtapi.resources.v2.models.RepairRequest;
import com.datastax.mgmtapi.resources.v2.models.RepairRequestResponse;
import com.datastax.mgmtapi.resources.v2.models.RingRange;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Test;

public class RepairResourcesV2Test {

  @Test
  public void testRepairResourcesSuccess() throws Exception {
    CqlService mockCqlService = mock(CqlService.class);
    ManagementApplication app =
        new ManagementApplication(
            null, null, new File("/tmp/cassandra.sock"), mockCqlService, null);
    ResultSet mockResultSet = mock(ResultSet.class);
    Row mockRow = mock(Row.class);
    when(mockResultSet.one()).thenReturn(mockRow);
    when(mockRow.getString(anyInt())).thenReturn("mockRepairID");
    when(mockCqlService.executePreparedStatement(
            any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mockResultSet);
    RepairResourcesV2 unit = new RepairResourcesV2(app);
    RepairRequest req =
        new RepairRequest(
            "keyspace",
            Collections.singletonList("table1"),
            false,
            true,
            Collections.singletonList(new RingRange("-100", "100")),
            RepairParallelism.DATACENTER_AWARE,
            Collections.EMPTY_LIST,
            1);
    Response resp = unit.repair(req);
    assertEquals(202, resp.getStatus());
    assertEquals("mockRepairID", ((RepairRequestResponse) resp.getEntity()).repairID);
    verify(mockCqlService)
        .executePreparedStatement(
            any(),
            eq("CALL NodeOps.repair(?, ?, ?, ?, ?, ?, ?, ?)"),
            eq("keyspace"),
            eq(Collections.singletonList("table1")),
            eq(false),
            eq(true),
            eq(RepairParallelism.DATACENTER_AWARE.getName()),
            eq(Collections.EMPTY_LIST),
            eq("-100:100"),
            eq(Integer.valueOf(1)));
  }

  @Test
  public void testRepairResourcesFail() throws Exception {
    CqlService mockCqlService = mock(CqlService.class);
    ManagementApplication app =
        new ManagementApplication(
            null, null, new File("/tmp/cassandra.sock"), mockCqlService, null);
    ResultSet mockResultSet = mock(ResultSet.class);
    Row mockRow = mock(Row.class);
    when(mockRow.getString(anyString())).thenReturn("mockrepairID");
    when(mockCqlService.executePreparedStatement(
            any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mockResultSet);
    RepairResourcesV2 unit = new RepairResourcesV2(app);
    List<String> tables = new ArrayList<>();
    tables.add("table1");
    tables.add("table2");
    RepairRequest req =
        new RepairRequest(
            "",
            tables,
            false,
            true,
            Collections.EMPTY_LIST,
            RepairParallelism.DATACENTER_AWARE,
            Collections.EMPTY_LIST,
            1);
    Response resp = unit.repair(req);
    assertEquals(500, resp.getStatus());
  }

  @Test
  public void testCancelAllRepairs() throws Exception {
    CqlService mockCqlService = mock(CqlService.class);
    ManagementApplication app =
        new ManagementApplication(
            null, null, new File("/tmp/cassandra.sock"), mockCqlService, null);
    RepairResourcesV2 unit = new RepairResourcesV2(app);
    Response resp = unit.cancelAllRepairs();
    assertEquals(202, resp.getStatus());
    verify(mockCqlService).executePreparedStatement(any(), eq("CALL NodeOps.stopAllRepairs()"));
  }

  @Test
  public void testGetRingRangeString() throws Exception {
    CqlService mockCqlService = mock(CqlService.class);
    ManagementApplication app =
        new ManagementApplication(
            null, null, new File("/tmp/cassandra.sock"), mockCqlService, null);
    RepairResourcesV2 unit = new RepairResourcesV2(app);
    List<RingRange> associatedTokens = new ArrayList<>();
    // add some random token ranges
    associatedTokens.add(new RingRange(-1506836194468667463l, -633835238802072494l));
    associatedTokens.add(new RingRange(-2976249057732638160l, -1506836194468667463l));
    associatedTokens.add(new RingRange(-6235755542119343496l, -2976249057732638160l));
    associatedTokens.add(new RingRange(-633835238802072494l, 660806372122351317l));
    associatedTokens.add(new RingRange(-7075332291273605506l, -6235755542119343496l));
    associatedTokens.add(new RingRange(2303998418447223636l, 7727458699102386551l));
    associatedTokens.add(new RingRange(660806372122351317l, 2303998418447223636l));
    associatedTokens.add(new RingRange(7727458699102386551l, -7075332291273605506l));
    assertEquals(
        "-1506836194468667463:-633835238802072494,"
            + "-2976249057732638160:-1506836194468667463,"
            + "-6235755542119343496:-2976249057732638160,"
            + "-633835238802072494:660806372122351317,"
            + "-7075332291273605506:-6235755542119343496,"
            + "2303998418447223636:7727458699102386551,"
            + "660806372122351317:2303998418447223636,"
            + "7727458699102386551:-7075332291273605506",
        unit.getRingRangeString(associatedTokens));
  }

  @Test
  public void testGetRingRangeStringNull() throws Exception {
    CqlService mockCqlService = mock(CqlService.class);
    ManagementApplication app =
        new ManagementApplication(
            null, null, new File("/tmp/cassandra.sock"), mockCqlService, null);
    RepairResourcesV2 unit = new RepairResourcesV2(app);
    // test a null ring range
    assertEquals(null, unit.getRingRangeString(null));
  }

  @Test
  public void testGetRingRangeStringEmpty() throws Exception {
    CqlService mockCqlService = mock(CqlService.class);
    ManagementApplication app =
        new ManagementApplication(
            null, null, new File("/tmp/cassandra.sock"), mockCqlService, null);
    RepairResourcesV2 unit = new RepairResourcesV2(app);
    // test a empty ring range
    assertEquals(null, unit.getRingRangeString(Collections.EMPTY_LIST));
  }
}
