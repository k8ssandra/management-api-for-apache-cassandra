/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v2;

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
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Assert;
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
            new ArrayList<RingRange>(),
            RepairParallelism.DATACENTER_AWARE,
            new ArrayList<String>(),
            1);
    Response resp = unit.repair(req);
    Assert.assertEquals(202, resp.getStatus());
    Assert.assertEquals("mockRepairID", ((RepairRequestResponse) resp.getEntity()).repairID);
    verify(mockCqlService)
        .executePreparedStatement(
            any(),
            eq("CALL NodeOps.repair(?, ?, ?, ?, ?, ?, ?, ?)"),
            eq("keyspace"),
            eq(Optional.of(Collections.singletonList("table1"))),
            eq(false),
            eq(true),
            eq(Optional.of("dc_parallel")),
            eq(Optional.empty()),
            eq(Optional.empty()),
            eq(Optional.of(1)));
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
            new ArrayList<RingRange>(),
            RepairParallelism.DATACENTER_AWARE,
            new ArrayList<String>(),
            1);
    Response resp = unit.repair(req);
    Assert.assertEquals(500, resp.getStatus());
  }
}
