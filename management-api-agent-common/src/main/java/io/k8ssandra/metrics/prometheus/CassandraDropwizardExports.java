/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.prometheus;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.datastax.mgmtapi.NodeOpsProvider;
import com.google.common.collect.Lists;
import io.k8ssandra.metrics.builder.CassandraMetricDefinition;
import io.k8ssandra.metrics.builder.CassandraMetricNameParser;
import io.k8ssandra.metrics.builder.CassandraMetricRegistryListener;
import io.k8ssandra.metrics.builder.CassandraMetricsTools;
import io.k8ssandra.metrics.builder.RefreshableMetricFamilySamples;
import io.k8ssandra.metrics.config.Configuration;
import io.prometheus.client.Collector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;

/**
 * Collect Dropwizard metrics from CassandraMetricRegistry. This is modified version of the
 * Prometheus' client_java's DropwizardExports to improve performance, parsing and correctness.
 */
public class CassandraDropwizardExports extends Collector implements Collector.Describable {

  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(CassandraDropwizardExports.class);
  private final MetricRegistry registry;

  private ConcurrentHashMap<String, RefreshableMetricFamilySamples> familyCache;

  private CassandraMetricRegistryListener listener;

  /**
   * Creates a new CassandraDropwizardExports with a custom {@link MetricFilter}.
   *
   * @param registry a metric registry to export in prometheus.
   * @param config a custom metric filter.
   */
  public CassandraDropwizardExports(MetricRegistry registry, Configuration config) {
    this.registry = registry;
    this.familyCache = new ConcurrentHashMap<>();
    listener = new CassandraMetricRegistryListener(this.familyCache, config);

    registry.addListener(listener);
    addReadinessMetric(config);
  }

  @Override
  public List<MetricFamilySamples> collect() {
    try {
      for (RefreshableMetricFamilySamples value : familyCache.values()) {
        value.refreshSamples();
      }

      return new ArrayList<>(familyCache.values());
    } catch (Exception e) {
      logger.error("Failed to parse metrics", e);
      throw new RuntimeException(e);
    }
  }

  private void addReadinessMetric(Configuration config) {
    CassandraMetricNameParser parser =
        new CassandraMetricNameParser(
            CassandraMetricsTools.DEFAULT_LABEL_NAMES,
            CassandraMetricsTools.DEFAULT_LABEL_VALUES,
            config);

    CassandraMetricDefinition proto =
        parser.parseDropwizardMetric(
            "org.apache.cassandra.metrics.readiness",
            "",
            Lists.newArrayList(),
            Lists.newArrayList());
    proto.setFiller(
        (samples) -> {
          int value = 0;
          try {
            value = NodeOpsProvider.instance.get().ready() ? 1 : 0;
          } catch (IOException e) {
            // It isn't ready, return 0
          }
          Collector.MetricFamilySamples.Sample sample =
              new Collector.MetricFamilySamples.Sample(
                  proto.getMetricName(), proto.getLabelNames(), proto.getLabelValues(), value);
          samples.add(sample);
        });

    RefreshableMetricFamilySamples familySamples =
        new RefreshableMetricFamilySamples(
            proto.getMetricName(), Collector.Type.GAUGE, "", new ArrayList<>());
    familySamples.addDefinition(proto);
    listener.updateCache(
        "org.apache.cassandra.metrics.readiness", proto.getMetricName(), familySamples);
  }

  @Override
  public List<MetricFamilySamples> describe() {
    return new ArrayList<>();
  }
}
