package io.k8ssandra.metrics.builder.relabel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import io.k8ssandra.metrics.builder.CassandraMetricDefinition;
import io.k8ssandra.metrics.builder.CassandraMetricNameParser;
import io.k8ssandra.metrics.config.Configuration;
import org.junit.Test;

public class SingleFilterTests {
  /**
   * - sourceLabels: [__name__] separator: "@" regex: "org_apache_cassandra_metrics_table_.*"
   * action: "drop"
   */
  @Test
  public void TestDropWithName() {
    RelabelSpec spec =
        new RelabelSpec(
            Lists.newArrayList("__name__"),
            "",
            "org_apache_cassandra_metrics_table_.*",
            "drop",
            "",
            "");
    Configuration config = new Configuration();
    config.setRelabels(Lists.newArrayList(spec));
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

    parser.replace("org.apache.cassandra.metrics.Table.RangeLatency", tableDefinition);
    assertFalse(tableDefinition.isKeep());

    CassandraMetricDefinition keyspaceDefinition =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_keyspace_range_latency_count",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system"));

    parser.replace("org.apache.cassandra.metrics.Keyspace.RangeLatency", keyspaceDefinition);
    assertTrue(keyspaceDefinition.isKeep());
  }

  /**
   * - sourceLabels: [__name__, table] separator: "@" regex:
   * "(org_apache_cassandra_metrics_table_.*)@dropped_columns" action: "keep"
   */
  @Test
  public void TestDropWithNameLabelCombo() {
    RelabelSpec spec =
        new RelabelSpec(
            Lists.newArrayList("__name__", "table"),
            "@",
            "(org_apache_cassandra_metrics_table_.*)@dropped_columns",
            "keep",
            "",
            "");
    Configuration config = new Configuration();
    config.setRelabels(Lists.newArrayList(spec));
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
    parser.replace("org.apache.cassandra.metrics.Table.RangeLatency", tableDefinition);
    assertFalse(tableDefinition.isKeep());

    CassandraMetricDefinition tableDefinitionKeep =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_table_estimated_partition_size_histogram",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace", "table"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3",
                "Test Cluster",
                "dc1",
                "rack1",
                "system",
                "dropped_columns"));
    parser.replace("org.apache.cassandra.metrics.Keyspace.RangeLatency", tableDefinitionKeep);
    assertTrue(tableDefinitionKeep.isKeep());

    CassandraMetricDefinition keyspaceDefinition =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_keyspace_range_latency_count",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system"));

    parser.replace("org.apache.cassandra.metrics.Keyspace.RangeLatency", keyspaceDefinition);
    assertFalse(keyspaceDefinition.isKeep());
  }

  /**
   * Drop all table metrics, except those with label table=dropped_columns Don't drop other metrics
   *
   * <p>- sourceLabels: [__name__, table] separator: "@" regex:
   * "(org_apache_cassandra_metrics_table_.*)@\b(?!dropped_columns\b)\w+" action: "drop"
   */
  @Test
  public void TestDropWithNameLabelComboWithExcept() {
    RelabelSpec spec =
        new RelabelSpec(
            Lists.newArrayList("__name__", "table"),
            "@",
            "(org_apache_cassandra_metrics_table_.*)@\\b(?!dropped_columns\\b)\\w+",
            "drop",
            "",
            "");

    Configuration config = new Configuration();
    config.setRelabels(Lists.newArrayList(spec));
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
    parser.replace("org.apache.cassandra.metrics.Table.RangeLatency", tableDefinition);
    assertFalse(tableDefinition.isKeep());

    CassandraMetricDefinition tableDefinitionKeep =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_table_estimated_partition_size_histogram",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace", "table"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3",
                "Test Cluster",
                "dc1",
                "rack1",
                "system",
                "dropped_columns"));
    parser.replace("org.apache.cassandra.metrics.Keyspace.RangeLatency", tableDefinitionKeep);
    assertTrue(tableDefinitionKeep.isKeep());

    CassandraMetricDefinition keyspaceDefinition =
        new CassandraMetricDefinition(
            "org_apache_cassandra_metrics_keyspace_range_latency_count",
            Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace"),
            Lists.newArrayList(
                "6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system"));

    parser.replace("org.apache.cassandra.metrics.Keyspace.RangeLatency", keyspaceDefinition);
    assertTrue(keyspaceDefinition.isKeep());
  }

  @Test
  public void OnlyMatchingLabel() {
    RelabelSpec tableLabelFilter =
        new RelabelSpec(Lists.newArrayList("table"), "@", ".+", "drop", "", "");
    Configuration config = new Configuration();
    config.setRelabels(Lists.newArrayList(tableLabelFilter));
    CassandraMetricNameParser parser =
        new CassandraMetricNameParser(Lists.newArrayList(), Lists.newArrayList(), config);

    CassandraMetricDefinition hasTableLabel =
        new CassandraMetricDefinition(
            "has_table_label", Lists.newArrayList("table"), Lists.newArrayList("value"));
    parser.replace("", hasTableLabel);
    assertFalse(hasTableLabel.isKeep());

    CassandraMetricDefinition hasNoLabels =
        new CassandraMetricDefinition(
            "has_table_label", Lists.newArrayList(), Lists.newArrayList());
    parser.replace("", hasNoLabels);
    assertTrue(hasNoLabels.isKeep());

    CassandraMetricDefinition hasOtherLabels =
        new CassandraMetricDefinition(
            "has_table_label", Lists.newArrayList("keyspace"), Lists.newArrayList("value"));
    parser.replace("", hasOtherLabels);
    assertTrue(hasOtherLabels.isKeep());
  }
}
