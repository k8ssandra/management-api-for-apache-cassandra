/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import io.k8ssandra.metrics.builder.relabel.RelabelSpec;
import io.k8ssandra.metrics.config.Configuration;
import io.k8ssandra.metrics.prometheus.CassandraDropwizardExports;
import io.prometheus.client.Collector;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class CassandraMetricRegistryListenerTest {

  private static final String PROMETHEUS_METRIC_NAME =
      "org_apache_cassandra_metrics_table_metric_name";
  private static final String KEYSPACE_NAME = "KeyspaceName";
  private static final String TABLE_NAME = "TableName";
  private static final String SIBLING_TABLE_NAME = "SiblingTable";

  @Test
  public void concurrentlyRegistersTableMetricsInSameFamily() throws Exception {
    CyclicBarrier containsKeyBarrier = new CyclicBarrier(2);
    ConcurrentHashMap<String, RefreshableMetricFamilySamples> familyCache =
        new ConcurrentHashMap<String, RefreshableMetricFamilySamples>() {
          @Override
          public boolean containsKey(Object key) {
            boolean containsKey = super.containsKey(key);
            if (PROMETHEUS_METRIC_NAME.equals(key)) {
              try {
                // Make both registrations observe the family as absent before either can put it.
                containsKeyBarrier.await(5, TimeUnit.SECONDS);
              } catch (Exception e) {
                throw new AssertionError("Registrations did not reach containsKey together", e);
              }
            }
            return containsKey;
          }
        };
    CassandraMetricRegistryListener listener =
        new CassandraMetricRegistryListener(familyCache, tableMetricConfiguration());
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<?> first =
          executor.submit(
              () -> listener.onCounterAdded(tableMetricName(TABLE_NAME), new Counter()));
      Future<?> second =
          executor.submit(
              () -> listener.onCounterAdded(tableMetricName(SIBLING_TABLE_NAME), new Counter()));

      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    RefreshableMetricFamilySamples family = familyCache.get(PROMETHEUS_METRIC_NAME);
    assertNotNull("Shared table metric family should be registered", family);
    assertEquals(
        "Both table definitions should be retained", 2, family.getDefinitions().size());
  }

  @Test
  public void removesTableMetricMergedIntoExistingFamily() throws Exception {
    MetricRegistry registry = new MetricRegistry();
    CassandraDropwizardExports exporter =
        new CassandraDropwizardExports(registry, tableMetricConfiguration());

    Counter sibling = registry.counter(tableMetricName(SIBLING_TABLE_NAME));
    sibling.inc(10);
    Counter target = registry.counter(tableMetricName(TABLE_NAME));
    target.inc();

    registry.remove(tableMetricName(TABLE_NAME));

    List<Collector.MetricFamilySamples> samples = exporter.collect();
    assertNull(
        "Removed table metric should no longer be exported",
        findTableSample(samples, KEYSPACE_NAME, TABLE_NAME));
    assertSampleValue(samples, SIBLING_TABLE_NAME, 10.0);
  }

  @Test
  public void removingFirstTableMetricKeepsSiblingInSameFamily() throws Exception {
    MetricRegistry registry = new MetricRegistry();
    CassandraDropwizardExports exporter =
        new CassandraDropwizardExports(registry, tableMetricConfiguration());

    Counter target = registry.counter(tableMetricName(TABLE_NAME));
    target.inc();
    Counter sibling = registry.counter(tableMetricName(SIBLING_TABLE_NAME));
    sibling.inc(10);

    registry.remove(tableMetricName(TABLE_NAME));

    List<Collector.MetricFamilySamples> samples = exporter.collect();
    assertNull(
        "Removed table metric should no longer be exported",
        findTableSample(samples, KEYSPACE_NAME, TABLE_NAME));
    assertSampleValue(samples, SIBLING_TABLE_NAME, 10.0);
  }

  @Test
  public void reRegistersRemovedTableMetric() throws Exception {
    MetricRegistry registry = new MetricRegistry();
    CassandraDropwizardExports exporter =
        new CassandraDropwizardExports(registry, tableMetricConfiguration());

    // Register a sibling first so both Dropwizard metrics are merged into the same Prometheus
    // family. The target metric therefore exercises updateCache's existing-family branch.
    Counter sibling = registry.counter(tableMetricName(SIBLING_TABLE_NAME));
    sibling.inc(10);

    String dropwizardName = tableMetricName(TABLE_NAME);
    Counter original = registry.counter(dropwizardName);
    original.inc();

    registry.remove(dropwizardName);

    Counter replacement = registry.counter(dropwizardName);
    replacement.inc(2);

    List<Collector.MetricFamilySamples> samples = exporter.collect();
    Collector.MetricFamilySamples.Sample sample =
        findTableSample(samples, KEYSPACE_NAME, TABLE_NAME);

    assertNotNull("Re-registered table metric should still be exported", sample);
    assertEquals("Sample should use the re-registered metric", 2.0, sample.value, 0.0);
    assertSampleValue(samples, SIBLING_TABLE_NAME, 10.0);
  }

  private String tableMetricName(String tableName) {
    return String.format(
        "org.apache.cassandra.metrics.Table.MetricName.%s.%s", KEYSPACE_NAME, tableName);
  }

  private Configuration tableMetricConfiguration() {
    String tableMetricPattern =
        "org\\.apache\\.cassandra\\.metrics\\.Table\\.(\\w+)\\.(\\w+)\\.(\\w+)";
    List<String> originalName = Collections.singletonList("__origname__");

    Configuration config = new Configuration();
    config.setRelabels(
        Arrays.asList(
            new RelabelSpec(originalName, "", tableMetricPattern, "", "keyspace", "$2"),
            new RelabelSpec(originalName, "", tableMetricPattern, "", "table", "$3"),
            new RelabelSpec(
                originalName,
                "",
                tableMetricPattern,
                "",
                "__name__",
                "org_apache_cassandra_metrics_table_$1")));
    return config;
  }

  private Collector.MetricFamilySamples.Sample findTableSample(
      List<Collector.MetricFamilySamples> families, String keyspaceName, String tableName) {
    for (Collector.MetricFamilySamples family : families) {
      for (Collector.MetricFamilySamples.Sample sample : family.samples) {
        if (PROMETHEUS_METRIC_NAME.equals(sample.name)
            && keyspaceName.equals(labelValue(sample, "keyspace"))
            && tableName.equals(labelValue(sample, "table"))) {
          return sample;
        }
      }
    }
    return null;
  }

  private void assertSampleValue(
      List<Collector.MetricFamilySamples> samples, String tableName, double expectedValue) {
    Collector.MetricFamilySamples.Sample sample =
        findTableSample(samples, KEYSPACE_NAME, tableName);
    assertNotNull(tableName + " metric should be exported", sample);
    assertEquals(expectedValue, sample.value, 0.0);
  }

  private String labelValue(Collector.MetricFamilySamples.Sample sample, String labelName) {
    int index = sample.labelNames.indexOf(labelName);
    return index < 0 ? null : sample.labelValues.get(index);
  }
}
