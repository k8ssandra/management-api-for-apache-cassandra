/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.prometheus;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import io.k8ssandra.metrics.config.Configuration;
import io.prometheus.client.Collector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.cassandra.db.compaction.OperationType;
import org.junit.Test;
import org.mockito.Mockito;

public class TaskExportsTests {

  @Test
  public void testStreamInfoStats() {
    MetricRegistry mockRegistry = mock(MetricRegistry.class);
    Configuration config = new Configuration();
    CassandraTasksExports exports = new CassandraTasksExports(mockRegistry, config);
    CassandraTasksExports spy = Mockito.spy(exports);
    Mockito.doReturn(getStreamInfoMock()).when(spy).getStreamInfos();
    when(spy.getStreamInfoStats()).thenCallRealMethod();

    List<Collector.MetricFamilySamples> streamInfoStats = spy.getStreamInfoStats();
    assertEquals(8, streamInfoStats.size());
    assertEquals(10, streamInfoStats.get(0).samples.size());
    for (Collector.MetricFamilySamples streamInfoStat : streamInfoStats) {
      for (Collector.MetricFamilySamples.Sample sample : streamInfoStat.samples) {
        assertEquals(9, sample.labelNames.size());
        assertEquals(sample.labelNames.size(), sample.labelValues.size());
      }
    }
  }

  @Test
  public void testCompactionStats() {
    MetricRegistry mockRegistry = mock(MetricRegistry.class);
    Configuration config = new Configuration();
    CassandraTasksExports exports = new CassandraTasksExports(mockRegistry, config);
    CassandraTasksExports spy = Mockito.spy(exports);
    Mockito.doReturn(getCompactionsMock()).when(spy).getCompactions();
    when(spy.getCompactionStats()).thenCallRealMethod();

    assertEquals(2, spy.getCompactionStats().size());
    for (Collector.MetricFamilySamples compactionStat : spy.getCompactionStats()) {
      assertEquals(1, compactionStat.samples.size());
      Collector.MetricFamilySamples.Sample sample = compactionStat.samples.get(0);
      assertEquals(10, sample.labelNames.size());
      assertEquals(sample.labelValues.size(), sample.labelNames.size());
    }
  }

  private List<Map<String, String>> getCompactionsMock() {
    ArrayList<Map<String, String>> results = Lists.newArrayList();

    Map<String, String> ret = new HashMap<>();
    ret.put("id", "");
    ret.put("keyspace", "getKeyspace()");
    ret.put("columnfamily", "getColumnFamily()");
    ret.put("completed", "1");
    ret.put("total", "2");
    ret.put("taskType", OperationType.COMPACTION.name());
    ret.put("unit", "unit.toString()");
    ret.put("compactionId", "compactionId");

    results.add(ret);

    return results;
  }

  private List<Map<String, List<Map<String, String>>>> getStreamInfoMock() {
    List<Map<String, List<Map<String, String>>>> result = new ArrayList<>();

    for (int i = 0; i < 10; i++) {
      Map<String, List<Map<String, String>>> streamInfo = new HashMap<>();
      List<Map<String, String>> sessionResults = new ArrayList<>();

      Map<String, String> sessionInfo = new HashMap<>();
      sessionInfo.put("STREAM_OPERATION", "testStreaming");
      sessionInfo.put("PEER", "127.0.0.1");
      sessionInfo.put("USING_CONNECTION", "127.0.0.1");
      sessionInfo.put("TOTAL_FILES_TO_RECEIVE", "10");
      sessionInfo.put("TOTAL_FILES_RECEIVED", "9");
      sessionInfo.put("TOTAL_SIZE_TO_RECEIVE", "128");
      sessionInfo.put("TOTAL_SIZE_RECEIVED", "127");

      sessionInfo.put("TOTAL_FILES_TO_SEND", "5");
      sessionInfo.put("TOTAL_FILES_SENT", "4");
      sessionInfo.put("TOTAL_SIZE_TO_SEND", "512");
      sessionInfo.put("TOTAL_SIZE_SENT", "511");
      sessionResults.add(sessionInfo);

      streamInfo.put("123456", sessionResults);

      result.add(streamInfo);
    }

    return result;
  }
}
