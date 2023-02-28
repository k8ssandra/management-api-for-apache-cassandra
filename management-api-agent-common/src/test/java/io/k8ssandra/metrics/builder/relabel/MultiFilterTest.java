/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.builder.relabel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import io.k8ssandra.metrics.builder.CassandraMetricDefinition;
import io.k8ssandra.metrics.builder.CassandraMetricNameParser;
import io.k8ssandra.metrics.config.Configuration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class MultiFilterTest {

  @Test
  public void DropMultipleMetrics() {
    // drop jvm_classes_loaded
    RelabelSpec dropJVM =
        new RelabelSpec(Lists.newArrayList("__name__"), "", "jvm_classes_loaded.*", "drop", "", "");

    // drop table metrics
    RelabelSpec spec =
        new RelabelSpec(
            Lists.newArrayList("__name__"),
            "",
            "org_apache_cassandra_metrics_table_.*",
            "drop",
            "",
            "");

    Configuration config = new Configuration();
    config.setRelabels(Lists.newArrayList(dropJVM, spec));

    CassandraMetricNameParser parser =
        new CassandraMetricNameParser(Lists.newArrayList(), Lists.newArrayList(), config);

    CassandraMetricDefinition tableDefinition =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_table_range_latency_count",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace", "table"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3",
                "Test Cluster",
                "dc1",
                "rack1",
                "system",
                "peers_v2"));

    CassandraMetricDefinition keyspaceDefinition =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_keyspace_range_latency_count",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system"));

    CassandraMetricDefinition jvmDefinition =
        new CassandraMetricDefinition(
            "jvm_classes_loaded_total", Lists.newArrayList(), Lists.newArrayList());

    List<CassandraMetricDefinition> definitions =
        Lists.newArrayList(tableDefinition, keyspaceDefinition, jvmDefinition);
    List<CassandraMetricDefinition> passed = new ArrayList<>(1);

    for (CassandraMetricDefinition definition : definitions) {
      parser.replace("", definition);
      if (definition.isKeep()) {
        passed.add(definition);
      }
    }

    assertEquals(1, passed.size());
  }

  @Test
  public void KeepAndDropSubset() {
    // Keep only production cluster metrics
    RelabelSpec clusterFilter =
        new RelabelSpec(Lists.newArrayList("cluster"), "@", "production", "keep", "", "");

    // But drop all with table label
    RelabelSpec tableLabelFilter =
        new RelabelSpec(Lists.newArrayList("table"), "@", ".+", "drop", "", "");

    Configuration config = new Configuration();
    config.setRelabels(Lists.newArrayList(clusterFilter, tableLabelFilter));

    CassandraMetricNameParser parser =
        new CassandraMetricNameParser(Lists.newArrayList(), Lists.newArrayList(), config);

    CassandraMetricDefinition tableDefinitionTest =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_table_range_latency_count",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace", "table"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3",
                "Test Cluster",
                "dc1",
                "rack1",
                "system",
                "peers_v2"));

    CassandraMetricDefinition tableDefinitionProd =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_table_range_latency_count",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace", "table"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3",
                "production",
                "dc1",
                "rack1",
                "system",
                "system_schema"));

    CassandraMetricDefinition keyspaceDefinitionProd =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_keyspace_range_latency_count",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "production", "dc1", "rack1", "system"));

    CassandraMetricDefinition keyspaceDefinitionTest =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_keyspace_range_latency_count",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system"));

    List<CassandraMetricDefinition> definitions =
        Lists.newArrayList(
            tableDefinitionTest,
            tableDefinitionProd,
            keyspaceDefinitionProd,
            keyspaceDefinitionTest);
    List<CassandraMetricDefinition> passed = new ArrayList<>(1);

    for (CassandraMetricDefinition definition : definitions) {
      parser.replace("", definition);
      if (definition.isKeep()) {
        passed.add(definition);
      }
    }

    assertEquals(1, passed.size());
    assertEquals(
        "org_apache_cassandra_metrics_keyspace_range_latency_count", passed.get(0).getMetricName());
    assertEquals("production", passed.get(0).getLabelValues().get(1));
  }

  @Test
  public void dropFromPreviousReplacement() {
    RelabelSpec tableExtractor =
        new RelabelSpec(
            Lists.newArrayList("__origname__"),
            "",
            "org\\.apache\\.cassandra\\.metrics\\.Table\\.(\\w+)\\.(\\w+)\\.(\\w+)",
            "replace",
            "table",
            "$3");
    RelabelSpec tableRenamer =
        new RelabelSpec(
            Lists.newArrayList("__origname__"),
            "",
            "org\\.apache\\.cassandra\\.metrics\\.Table\\.(\\w+)\\.(\\w+)\\.(\\w+)",
            "replace",
            "__name__",
            "org_apache_cassandra_metrics_table_$1");
    RelabelSpec keepDroppedColumns =
        new RelabelSpec(
            Lists.newArrayList("__name__", "table"),
            "@",
            "(org_apache_cassandra_metrics_table_.*)@\\b(?!DroppedColumns\\b)\\w+",
            "drop",
            "",
            "");

    Configuration config = new Configuration();
    config.setRelabels(Lists.newArrayList(tableExtractor, tableRenamer, keepDroppedColumns));
    CassandraMetricNameParser parser =
        new CassandraMetricNameParser(Lists.newArrayList(), Lists.newArrayList(), config);

    CassandraMetricDefinition tableMetric =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.Table.MetricName.KeyspaceName.DroppedColumns",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());
    CassandraMetricDefinition tableMetricToDrop =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.Table.MetricName.KeyspaceName.SecondaryTable",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());
    assertTrue(tableMetric.isKeep());
    assertFalse(tableMetricToDrop.isKeep());
  }

  @Test
  public void verifyTagValueIsOverridden() {
    RelabelSpec tableExtractor =
        new RelabelSpec(
            Lists.newArrayList("__origname__"),
            "",
            "org\\.apache\\.cassandra\\.metrics\\.Table\\.(\\w+)\\.(\\w+)\\.(\\w+)",
            "replace",
            "table",
            "$3");

    RelabelSpec addFirstTagValue =
        new RelabelSpec(Lists.newArrayList("table"), "", ".+", "", "should_drop", "true");

    RelabelSpec addSecondTagValue =
        new RelabelSpec(Lists.newArrayList("table"), "", ".+", "", "should_drop", "false");

    Configuration config = new Configuration();
    config.setRelabels(Lists.newArrayList(tableExtractor, addFirstTagValue, addSecondTagValue));
    CassandraMetricNameParser parser =
        new CassandraMetricNameParser(Lists.newArrayList(), Lists.newArrayList(), config);

    CassandraMetricDefinition aMetric =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.Table.MetricName.KeyspaceName.DroppedColumns",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());

    assertEquals(2, aMetric.getLabelValues().size());
    assertEquals(2, aMetric.getLabelNames().size());

    assertEquals("table", aMetric.getLabelNames().get(0));
    assertEquals("should_drop", aMetric.getLabelNames().get(1));
    assertEquals("false", aMetric.getLabelValues().get(1));
  }
}
