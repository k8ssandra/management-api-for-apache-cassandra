/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import com.datastax.mgmtapi.shims.CassandraAPI;
import com.datastax.mgmtapi.util.Job;
import com.datastax.mgmtapi.util.JobExecutor;
import com.datastax.oss.driver.api.core.CqlSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cassandra.repair.messages.RepairOption;
import org.apache.cassandra.service.StorageService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NodeOpsProviderTest {

  @Mock private StorageService storageService;
  @Mock private JobExecutor jobExecutor;
  @Mock private CqlSession session;
  @Mock private CassandraAPI cassandraApi;

  private NodeOpsProvider nodeOpsProvider;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    nodeOpsProvider = new NodeOpsProvider();
    ShimLoader.instance = () -> cassandraApi;
    NodeOpsProvider.service = jobExecutor;
  }

  @Test
  public void testBasicRepair() throws IOException {
    String keyspace = "testKeyspace";
    List<String> tables = null;
    Boolean full = true;
    boolean notifications = false;
    String repairParallelism = "sequential";
    List<String> datacenters = null;
    String ringRangeString = null;
    Integer repairThreadCount = null;

    Map<String, String> repairSpec = new HashMap<>();
    repairSpec.put(RepairOption.INCREMENTAL_KEY, "false");
    repairSpec.put(RepairOption.PARALLELISM_KEY, "sequential");
    repairSpec.put(RepairOption.TRACE_KEY, "false");

    when(cassandraApi.getStorageService()).thenReturn(storageService);
    when(storageService.repairAsync(eq(keyspace), anyMap())).thenReturn(1);

    String jobId = nodeOpsProvider.repair(
      keyspace, tables, full, notifications, repairParallelism, datacenters, ringRangeString, repairThreadCount);

    verify(storageService).repairAsync(eq(keyspace), eq(repairSpec));
    assertEquals("1", jobId);
  }

  @Test
  public void testFullRepair() throws IOException {
    String keyspace = "testKeyspace";
    List<String> tables = null;
    Boolean full = true;
    boolean notifications = false;
    String repairParallelism = "dc_parallel";
    List<String> datacenters = null;
    String ringRangeString = null;
    Integer repairThreadCount = null;

    Map<String, String> repairSpec = new HashMap<>();
    repairSpec.put(RepairOption.INCREMENTAL_KEY, "false");
    repairSpec.put(RepairOption.PARALLELISM_KEY, "dc_parallel");
    repairSpec.put(RepairOption.TRACE_KEY, "false");

    when(cassandraApi.getStorageService()).thenReturn(storageService);
    when(storageService.repairAsync(eq(keyspace), anyMap())).thenReturn(1);

    String jobId = nodeOpsProvider.repair(
      keyspace, tables, full, notifications, repairParallelism, datacenters, ringRangeString, repairThreadCount);

    verify(storageService).repairAsync(eq(keyspace), eq(repairSpec));
    assertEquals("1", jobId);
  }

  @Test
  public void testIncrementalRepair() throws IOException {
    String keyspace = "testKeyspace";
    List<String> tables = null;
    Boolean full = false;
    boolean notifications = false;
    String repairParallelism = "parallel";
    List<String> datacenters = null;
    String ringRangeString = null;
    Integer repairThreadCount = null;

    Map<String, String> repairSpec = new HashMap<>();
    repairSpec.put(RepairOption.INCREMENTAL_KEY, "true");
    repairSpec.put(RepairOption.PARALLELISM_KEY, "parallel");
    repairSpec.put(RepairOption.TRACE_KEY, "false");

    when(cassandraApi.getStorageService()).thenReturn(storageService);
    when(storageService.repairAsync(eq(keyspace), anyMap())).thenReturn(1);

    String jobId = nodeOpsProvider.repair(
      keyspace, tables, full, notifications, repairParallelism, datacenters, ringRangeString, repairThreadCount);

    verify(storageService).repairAsync(eq(keyspace), eq(repairSpec));
    assertEquals("1", jobId);
  }

  @Test
  public void testIncrementalSequentialRepair() throws IOException {
    String keyspace = "testKeyspace";
    List<String> tables = null;
    Boolean full = false;
    boolean notifications = false;
    String repairParallelism = "sequential";
    List<String> datacenters = null;
    String ringRangeString = null;
    Integer repairThreadCount = null;

    Map<String, String> repairSpec = new HashMap<>();
    repairSpec.put(RepairOption.INCREMENTAL_KEY, "true");
    // We expect the parallelism to be overridden to parallel
    repairSpec.put(RepairOption.PARALLELISM_KEY, "parallel");
    repairSpec.put(RepairOption.TRACE_KEY, "false");

    when(cassandraApi.getStorageService()).thenReturn(storageService);
    when(storageService.repairAsync(eq(keyspace), anyMap())).thenReturn(1);

    String jobId = nodeOpsProvider.repair(
      keyspace, tables, full, notifications, repairParallelism, datacenters, ringRangeString, repairThreadCount);

    verify(storageService).repairAsync(eq(keyspace), eq(repairSpec));
    assertEquals("1", jobId);
  }

  @Test
  public void testRepairWithNotifications() throws IOException {
    String keyspace = "testKeyspace";
    List<String> tables = null;
    Boolean full = true;
    boolean notifications = true;
    String repairParallelism = "parallel";
    List<String> datacenters = null;
    String ringRangeString = null;
    Integer repairThreadCount = null;

    Map<String, String> repairSpec = new HashMap<>();
    repairSpec.put(RepairOption.INCREMENTAL_KEY, "false");
    repairSpec.put(RepairOption.PARALLELISM_KEY, "parallel");
    repairSpec.put(RepairOption.TRACE_KEY, "false");

    when(cassandraApi.getStorageService()).thenReturn(storageService);
    when(storageService.repairAsync(eq(keyspace), anyMap())).thenReturn(1);

    Job job = mock(Job.class);
    when(jobExecutor.createJob(any(), any())).thenReturn(job);
    when(job.getJobId()).thenReturn("repair-1");
    when(jobExecutor.createJob(any(), any()))
      .thenReturn(job);

    String jobId = nodeOpsProvider.repair(
      keyspace, tables, full, notifications, repairParallelism, datacenters, ringRangeString, repairThreadCount);

    verify(storageService).repairAsync(eq(keyspace), eq(repairSpec));
    assertEquals("repair-1", jobId);
  }

  @Test
  public void testRepairWithDatacenters() throws IOException {
    String keyspace = "testKeyspace";
    List<String> tables = null;
    Boolean full = true;
    boolean notifications = false;
    String repairParallelism = "parallel";
    List<String> datacenters = Arrays.asList("dc1", "dc2");
    String ringRangeString = null;
    Integer repairThreadCount = null;

    Map<String, String> repairSpec = new HashMap<>();
    repairSpec.put(RepairOption.INCREMENTAL_KEY, "false");
    repairSpec.put(RepairOption.PARALLELISM_KEY, "parallel");
    repairSpec.put(RepairOption.TRACE_KEY, "false");
    repairSpec.put(RepairOption.DATACENTERS_KEY, "dc1,dc2");

    when(cassandraApi.getStorageService()).thenReturn(storageService);
    when(storageService.repairAsync(eq(keyspace), anyMap())).thenReturn(1);

    String jobId = nodeOpsProvider.repair(
      keyspace, tables, full, notifications, repairParallelism, datacenters, ringRangeString, repairThreadCount);

    verify(storageService).repairAsync(eq(keyspace), eq(repairSpec));
    assertEquals("1", jobId);
  }

  @Test
  public void testRepairWithRingRange() throws IOException {
    String keyspace = "testKeyspace";
    List<String> tables = null;
    Boolean full = true;
    boolean notifications = false;
    String repairParallelism = "parallel";
    List<String> datacenters = null;
    String ringRangeString = "0:100";
    Integer repairThreadCount = null;

    Map<String, String> repairSpec = new HashMap<>();
    repairSpec.put(RepairOption.INCREMENTAL_KEY, "false");
    repairSpec.put(RepairOption.PARALLELISM_KEY, "parallel");
    repairSpec.put(RepairOption.TRACE_KEY, "false");
    repairSpec.put(RepairOption.RANGES_KEY, "0:100");

    when(cassandraApi.getStorageService()).thenReturn(storageService);
    when(storageService.repairAsync(eq(keyspace), anyMap())).thenReturn(1);

    String jobId = nodeOpsProvider.repair(
      keyspace, tables, full, notifications, repairParallelism, datacenters, ringRangeString, repairThreadCount);

    verify(storageService).repairAsync(eq(keyspace), eq(repairSpec));
    assertEquals("1", jobId);
  }

  @Test
  public void testRepairWithThreadCount() throws IOException {
    String keyspace = "testKeyspace";
    List<String> tables = null;
    Boolean full = true;
    boolean notifications = false;
    String repairParallelism = "parallel";
    List<String> datacenters = null;
    String ringRangeString = null;
    Integer repairThreadCount = 2;

    Map<String, String> repairSpec = new HashMap<>();
    repairSpec.put(RepairOption.INCREMENTAL_KEY, "false");
    repairSpec.put(RepairOption.PARALLELISM_KEY, "parallel");
    repairSpec.put(RepairOption.TRACE_KEY, "false");
    repairSpec.put(RepairOption.JOB_THREADS_KEY, "2");

    when(cassandraApi.getStorageService()).thenReturn(storageService);
    when(storageService.repairAsync(eq(keyspace), anyMap())).thenReturn(1);

    String jobId = nodeOpsProvider.repair(
      keyspace, tables, full, notifications, repairParallelism, datacenters, ringRangeString, repairThreadCount);

    verify(storageService).repairAsync(eq(keyspace), eq(repairSpec));
    assertEquals("1", jobId);
  }

  @Test(expected = IOException.class)
  public void testRepairWithInvalidThreadCount() throws IOException {
    String keyspace = "testKeyspace";
    List<String> tables = null;
    Boolean full = true;
    boolean notifications = false;
    String repairParallelism = "parallel";
    List<String> datacenters = null;
    String ringRangeString = null;
    Integer repairThreadCount = 0;

    nodeOpsProvider.repair(
      keyspace, tables, full, notifications, repairParallelism, datacenters, ringRangeString, repairThreadCount);
  }
}