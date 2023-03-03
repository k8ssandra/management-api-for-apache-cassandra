/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.builder;

import static io.k8ssandra.metrics.builder.CassandraMetricsTools.LATENCY_BUCKETS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import io.k8ssandra.metrics.builder.relabel.RelabelSpec;
import io.k8ssandra.metrics.config.Configuration;
import io.k8ssandra.metrics.prometheus.CassandraDropwizardExports;
import io.prometheus.client.Collector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.cassandra.metrics.CassandraMetricsRegistry;
import org.apache.cassandra.metrics.DefaultNameFactory;
import org.junit.Test;

public class MetricsRegistryTest {

  // We need this default filter, because the CassandraMetricsRegistry might have some metrics
  // already registered when it starts up (and it does)
  private RelabelSpec specDefault =
      new RelabelSpec(
          Lists.newArrayList("__name__"),
          "",
          "org_apache_cassandra_metrics_test.*",
          "keep",
          "",
          "");

  @Test
  public void verifyRegistryListener() throws Exception {
    CassandraMetricsRegistry registry = CassandraMetricsRegistry.Metrics;
    Configuration config = new Configuration();
    config.setRelabels(Arrays.asList(specDefault));
    CassandraDropwizardExports exporter = new CassandraDropwizardExports(registry, config);
    int metricsCount = 10;
    for (int i = 0; i < metricsCount; i++) {
      registry.counter(createMetricName(String.format("c_nr_%d", i)));
      registry.meter(createMetricName(String.format("m_nr_%d", i)));
      registry.timer(createMetricName(String.format("t_nr_%d", i)));
      registry.histogram(createMetricName(String.format("h_nr_%d", i)), false);
      registry.register(createMetricName(String.format("g_nr_%d", i)), (Gauge<Integer>) () -> 3);
      registry.register(
          createMetricName(String.format("gh_nr_%d", i)),
          (Gauge<long[]>) () -> new long[] {1, 2, 3, 0});
    }

    List<Collector.MetricFamilySamples> collect = exporter.collect();
    int firstCollectSize = collect.size();
    collect = exporter.collect();
    int secondCollectSize = collect.size();

    assertTrue(firstCollectSize > 0);
    assertEquals(firstCollectSize, secondCollectSize);

    for (int i = 0; i < metricsCount; i++) {
      registry.remove(createMetricName(String.format("c_nr_%d", i)));
      registry.remove(createMetricName(String.format("m_nr_%d", i)));
      registry.remove(createMetricName(String.format("t_nr_%d", i)));
      registry.remove(createMetricName(String.format("h_nr_%d", i)));
      registry.remove(createMetricName(String.format("g_nr_%d", i)));
      registry.remove(createMetricName(String.format("gh_nr_%d", i)));
    }

    collect = exporter.collect();
    assertEquals(0, collect.size());
  }

  @Test
  public void verifyRegistryFilteredListener() throws Exception {
    CassandraMetricsRegistry registry = CassandraMetricsRegistry.Metrics;
    RelabelSpec spec =
        new RelabelSpec(
            Lists.newArrayList("__name__"),
            "",
            "org_apache_cassandra_metrics_test_g_a.*",
            "drop",
            "",
            "");
    Configuration config = new Configuration();
    config.setRelabels(Arrays.asList(specDefault, spec));
    CassandraDropwizardExports exporter = new CassandraDropwizardExports(registry, config);
    int metricsCount = 10;
    for (int i = 0; i < metricsCount; i++) {
      registry.register(createMetricName(String.format("g_nr_%d", i)), (Gauge<Integer>) () -> 3);
      registry.register(createMetricName(String.format("g_a_%d", i)), (Gauge<Integer>) () -> 3);
    }
    List<Collector.MetricFamilySamples> collect = exporter.collect();
    assertEquals(metricsCount, collect.size());

    for (int i = 0; i < metricsCount; i++) {
      registry.remove(createMetricName(String.format("g_nr_%d", i)));
      registry.remove(createMetricName(String.format("g_a_%d", i)));
    }
    collect = exporter.collect();
    assertEquals(0, collect.size());
  }

  @Test
  public void timerTest() throws Exception {
    CassandraMetricsRegistry registry = CassandraMetricsRegistry.Metrics;
    Configuration config = new Configuration();
    //    config.setRelabels(Arrays.asList(specDefault));
    CassandraDropwizardExports exporter = new CassandraDropwizardExports(registry, config);

    Timer timer = registry.timer(createMetricName("test_timer"));
    timer.update(42, TimeUnit.NANOSECONDS);
    timer.update(100, TimeUnit.NANOSECONDS);
    timer.update(42, TimeUnit.MICROSECONDS);
    timer.update(100, TimeUnit.MICROSECONDS);
    timer.update(42, TimeUnit.MILLISECONDS);
    timer.update(100, TimeUnit.MILLISECONDS);
    timer.update(100, TimeUnit.SECONDS);
    timer.update(100, TimeUnit.MINUTES);

    List<Collector.MetricFamilySamples> collect = exporter.collect();

    HashMap<String, Double> bucketMap = new HashMap<>(LATENCY_BUCKETS.length + 1);
    Double countValue = -1.0D;
    Double sumValue = -1.0D;
    for (Collector.MetricFamilySamples mfs : collect) {
      for (Collector.MetricFamilySamples.Sample s : mfs.samples) {
        if (s.name.equals("org_apache_cassandra_metrics_test_test_timer_test_bucket")) {
          bucketMap.put(s.labelValues.get(5), s.value);
        } else if (s.name.equals("org_apache_cassandra_metrics_test_test_timer_test_count")) {
          countValue = s.value;
        } else if (s.name.equals("org_apache_cassandra_metrics_test_test_timer_test_sum")) {
          sumValue = s.value;
        }
      }
    }

    assertEquals(LATENCY_BUCKETS.length + 1, bucketMap.keySet().size());
    assertEquals(countValue, bucketMap.get("+Inf"));
    assertTrue(countValue > 0);
    assertTrue(sumValue > 0);

    registry.remove(createMetricName("test_timer"));
  }

  private CassandraMetricsRegistry.MetricName createMetricName(String name) {
    return DefaultNameFactory.createMetricName("test", name, "test");
  }
}
