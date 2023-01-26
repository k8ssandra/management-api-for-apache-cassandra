package io.k8ssandra.metrics.builder.filter;

import com.google.common.collect.Lists;
import io.k8ssandra.metrics.builder.CassandraMetricDefinition;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SingleFilterTests {
    /**
         - sourceLabels: [__name__]
           separator: "@"
           regex: "org_apache_cassandra_metrics_table_.*"
           action: "drop"
     */
    @Test
    public void TestDropWithName() {
        RelabelSpec spec = new RelabelSpec(Lists.newArrayList("__name__"), "", "org_apache_cassandra_metrics_table_.*", "drop");
        CassandraMetricDefinitionFilter filter = new CassandraMetricDefinitionFilter(Lists.newArrayList(spec));

        CassandraMetricDefinition tableDefinition = new CassandraMetricDefinition("org_apache_cassandra_metrics_table_range_latency_count",
                Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace", "table"),
                Lists.newArrayList("6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system", "peers_v2"));

        assertFalse(filter.matches(tableDefinition, "org.apache.cassandra.metrics.Table.RangeLatency"));

        CassandraMetricDefinition keyspaceDefinition = new CassandraMetricDefinition("org_apache_cassandra_metrics_keyspace_range_latency_count",
                Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace"),
                Lists.newArrayList("6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system"));

        assertTrue(filter.matches(keyspaceDefinition, "org.apache.cassandra.metrics.Keyspace.RangeLatency"));
    }

    /**
     - sourceLabels: [__name__, table]
       separator: "@"
       regex: "(org_apache_cassandra_metrics_table_.*)@dropped_columns"
       action: "keep"
     */
    @Test
    public void TestDropWithNameLabelCombo() {
        RelabelSpec spec = new RelabelSpec(Lists.newArrayList("__name__", "table"), "@", "(org_apache_cassandra_metrics_table_.*)@dropped_columns", "keep");
        CassandraMetricDefinitionFilter filter = new CassandraMetricDefinitionFilter(Lists.newArrayList(spec));

        CassandraMetricDefinition tableDefinition = new CassandraMetricDefinition("org_apache_cassandra_metrics_table_range_latency_count",
                Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace", "table"),
                Lists.newArrayList("6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system", "peers_v2"));
        assertFalse(filter.matches(tableDefinition, "org.apache.cassandra.metrics.Table.RangeLatency"));

        CassandraMetricDefinition tableDefinitionKeep = new CassandraMetricDefinition("org_apache_cassandra_metrics_table_estimated_partition_size_histogram",
                Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace", "table"),
                Lists.newArrayList("6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system", "dropped_columns"));
        assertTrue(filter.matches(tableDefinitionKeep, "org.apache.cassandra.metrics.Keyspace.RangeLatency"));

        CassandraMetricDefinition keyspaceDefinition = new CassandraMetricDefinition("org_apache_cassandra_metrics_keyspace_range_latency_count",
                Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace"),
                Lists.newArrayList("6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system"));

        assertFalse(filter.matches(keyspaceDefinition, "org.apache.cassandra.metrics.Keyspace.RangeLatency"));
    }

    /**
     * Drop all table metrics, except those with label table=dropped_columns
     * Don't drop other metrics

     - sourceLabels: [__name__, table]
       separator: "@"
       regex: "(org_apache_cassandra_metrics_table_.*)@\b(?!dropped_columns\b)\w+"
       action: "drop"
     */
    @Test
    public void TestDropWithNameLabelComboWithExcept() {
        RelabelSpec spec = new RelabelSpec(Lists.newArrayList("__name__", "table"), "@", "(org_apache_cassandra_metrics_table_.*)@\\b(?!dropped_columns\\b)\\w+", "drop");
        CassandraMetricDefinitionFilter filter = new CassandraMetricDefinitionFilter(Lists.newArrayList(spec));

        CassandraMetricDefinition tableDefinition = new CassandraMetricDefinition("org_apache_cassandra_metrics_table_range_latency_count",
                Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace", "table"),
                Lists.newArrayList("6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system", "peers_v2"));
        assertFalse(filter.matches(tableDefinition, "org.apache.cassandra.metrics.Table.RangeLatency"));

        CassandraMetricDefinition tableDefinitionKeep = new CassandraMetricDefinition("org_apache_cassandra_metrics_table_estimated_partition_size_histogram",
                Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace", "table"),
                Lists.newArrayList("6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system", "dropped_columns"));
        assertTrue(filter.matches(tableDefinitionKeep, "org.apache.cassandra.metrics.Keyspace.RangeLatency"));

        CassandraMetricDefinition keyspaceDefinition = new CassandraMetricDefinition("org_apache_cassandra_metrics_keyspace_range_latency_count",
                Lists.newArrayList("host", "cluster", "datacenter", "rack", "keyspace"),
                Lists.newArrayList("6cc2e5ce-e73f-4592-8d02-fd5e17a070e3", "Test Cluster", "dc1", "rack1", "system"));

        assertTrue(filter.matches(keyspaceDefinition, "org.apache.cassandra.metrics.Keyspace.RangeLatency"));
    }

    @Test
    public void OnlyMatchingLabel() {
        RelabelSpec tableLabelFilter = new RelabelSpec(Lists.newArrayList("table"), "@", ".+", "drop");
        CassandraMetricDefinitionFilter filter = new CassandraMetricDefinitionFilter(Lists.newArrayList(tableLabelFilter));

        CassandraMetricDefinition hasTableLabel = new CassandraMetricDefinition("has_table_label", Lists.newArrayList("table"), Lists.newArrayList("value"));
        assertFalse(filter.matches(hasTableLabel, ""));

        CassandraMetricDefinition hasNoLabels = new CassandraMetricDefinition("has_table_label", Lists.newArrayList(), Lists.newArrayList());
        assertTrue(filter.matches(hasNoLabels, ""));

        CassandraMetricDefinition hasOtherLabels = new CassandraMetricDefinition("has_table_label", Lists.newArrayList("keyspace"), Lists.newArrayList("value"));
        assertTrue(filter.matches(hasOtherLabels, ""));
    }

}
