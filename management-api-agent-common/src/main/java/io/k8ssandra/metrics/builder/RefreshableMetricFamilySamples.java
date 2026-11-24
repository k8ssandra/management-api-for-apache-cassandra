/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.builder;

import io.prometheus.client.Collector;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefreshableMetricFamilySamples extends Collector.MetricFamilySamples {
  private static final Logger logger =
      LoggerFactory.getLogger(RefreshableMetricFamilySamples.class);
  private final Set<CassandraMetricDefinition> definitions;

  public RefreshableMetricFamilySamples(
      String name, Collector.Type type, String help, List<Sample> samples) {
    super(name, type, help, samples);
    definitions = new ConcurrentSkipListSet<>();
  }

  public void refreshSamples() {
    // Fetch all linked metricDefinitions
    samples.clear();
    for (CassandraMetricDefinition definition : definitions) {
      try {
        definition.accept(samples);
      } catch (RuntimeException e) {
        logger.warn(
            "Failed to collect samples for metric definition {}", definition.getMetricName(), e);
      }
    }
  }

  public void addDefinition(CassandraMetricDefinition definition) {
    definitions.add(definition);
  }

  public Set<CassandraMetricDefinition> getDefinitions() {
    return definitions;
  }
}
