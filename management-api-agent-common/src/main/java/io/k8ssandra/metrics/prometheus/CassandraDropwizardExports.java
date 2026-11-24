/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.prometheus;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import io.k8ssandra.metrics.builder.CassandraMetricRegistryListener;
import io.k8ssandra.metrics.builder.RefreshableMetricFamilySamples;
import io.k8ssandra.metrics.config.Configuration;
import io.prometheus.client.Collector;
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

  /**
   * Creates a new CassandraDropwizardExports with a custom {@link MetricFilter}.
   *
   * @param registry a metric registry to export in prometheus.
   * @param config a custom metric filter.
   */
  public CassandraDropwizardExports(MetricRegistry registry, Configuration config)
      throws NoSuchMethodException {
    this.registry = registry;
    this.familyCache = new ConcurrentHashMap<>();

    registry.addListener(new CassandraMetricRegistryListener(this.familyCache, config));
  }

  @Override
  public List<MetricFamilySamples> collect() {
    List<MetricFamilySamples> families = new ArrayList<>(familyCache.size());
    for (RefreshableMetricFamilySamples value : familyCache.values()) {
      try {
        value.refreshSamples();
        families.add(value);
      } catch (RuntimeException e) {
        // Skip the whole family: a partially refreshed histogram would produce wrong values
        logger.warn("Failed to refresh metric family {}, skipping it", value.name, e);
      }
    }

    return families;
  }

  @Override
  public List<MetricFamilySamples> describe() {
    return new ArrayList<>();
  }
}
