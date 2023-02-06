package io.k8ssandra.metrics.builder.relabel;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import io.k8ssandra.metrics.builder.CassandraMetricDefinition;
import io.k8ssandra.metrics.builder.CassandraMetricNameParser;
import io.k8ssandra.metrics.config.ConfigReader;
import io.k8ssandra.metrics.config.Configuration;
import org.junit.Test;

public class CassandraMetricsTest {

  /** Read the packaged collector-full.yaml and ensure that the rules are correct */
  @Test
  public void smokeTest() {
    Configuration configuration = ConfigReader.readConfig();
    CassandraMetricNameParser parser =
        new CassandraMetricNameParser(Lists.newArrayList(), Lists.newArrayList(), configuration);

    // Table
    CassandraMetricDefinition tableMetric =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.Table.MetricName.KeyspaceName.TableName",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());
    assertEquals("org_apache_cassandra_metrics_table_metric_name", tableMetric.getMetricName());
    assertEquals(2, tableMetric.getLabelNames().size());
    assertEquals(2, tableMetric.getLabelValues().size());
    assertEquals("table", tableMetric.getLabelNames().get(1));
    assertEquals("keyspace", tableMetric.getLabelNames().get(0));
    assertEquals("TableName", tableMetric.getLabelValues().get(1));
    assertEquals("KeyspaceName", tableMetric.getLabelValues().get(0));

    // Keyspace
    CassandraMetricDefinition keyspaceMetric =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.keyspace.MetricName.KeyspaceName",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());
    assertEquals(
        "org_apache_cassandra_metrics_keyspace_metric_name", keyspaceMetric.getMetricName());
    assertEquals(1, keyspaceMetric.getLabelNames().size());
    assertEquals(1, keyspaceMetric.getLabelValues().size());
    assertEquals("keyspace", keyspaceMetric.getLabelNames().get(0));
    assertEquals("KeyspaceName", keyspaceMetric.getLabelValues().get(0));

    // ThreadPool Metrics
    CassandraMetricDefinition threadPoolMetric =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.ThreadPools.MetricName.Path.ThreadPoolName",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());
    assertEquals(
        "org_apache_cassandra_metrics_thread_pools_metric_name", threadPoolMetric.getMetricName());
    assertEquals(2, threadPoolMetric.getLabelNames().size());
    assertEquals(2, threadPoolMetric.getLabelValues().size());
    assertEquals("pool_type", threadPoolMetric.getLabelNames().get(0));
    assertEquals("pool_name", threadPoolMetric.getLabelNames().get(1));
    assertEquals("Path", threadPoolMetric.getLabelValues().get(0));
    assertEquals("ThreadPoolName", threadPoolMetric.getLabelValues().get(1));

    // Client Request Metrics
    CassandraMetricDefinition clientRequestMetric =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.ClientRequest.MetricName.RequestType",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());
    assertEquals(
        "org_apache_cassandra_metrics_client_request_metric_name",
        clientRequestMetric.getMetricName());
    assertEquals(1, clientRequestMetric.getLabelNames().size());
    assertEquals(1, clientRequestMetric.getLabelValues().size());
    assertEquals("request_type", clientRequestMetric.getLabelNames().get(0));
    assertEquals("RequestType", clientRequestMetric.getLabelValues().get(0));

    // Cache Metrics
    CassandraMetricDefinition cacheMetric =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.Cache.MetricName.CacheName",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());
    assertEquals("org_apache_cassandra_metrics_cache_metric_name", cacheMetric.getMetricName());
    assertEquals(1, cacheMetric.getLabelNames().size());
    assertEquals(1, cacheMetric.getLabelValues().size());
    assertEquals("cache", cacheMetric.getLabelNames().get(0));
    assertEquals("CacheName", cacheMetric.getLabelValues().get(0));

    // DroppedMessage metrics
    CassandraMetricDefinition droppedMetric =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.DroppedMessage.MetricName.Type",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());
    assertEquals(
        "org_apache_cassandra_metrics_dropped_message_metric_name", droppedMetric.getMetricName());
    assertEquals(1, droppedMetric.getLabelNames().size());
    assertEquals(1, droppedMetric.getLabelValues().size());
    assertEquals("message_type", droppedMetric.getLabelNames().get(0));
    assertEquals("Type", droppedMetric.getLabelValues().get(0));

    // Streaming metrics
    CassandraMetricDefinition streamingMetric =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.Streaming.MetricName.127.0.0.1",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());
    assertEquals(
        "org_apache_cassandra_metrics_streaming_metric_name", streamingMetric.getMetricName());
    assertEquals(1, streamingMetric.getLabelNames().size());
    assertEquals(1, streamingMetric.getLabelValues().size());
    assertEquals("peer_ip", streamingMetric.getLabelNames().get(0));
    assertEquals("127.0.0.1", streamingMetric.getLabelValues().get(0));
  }
}
