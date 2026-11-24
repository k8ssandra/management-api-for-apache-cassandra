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

public class RefreshableMetricFamilySamples extends Collector.MetricFamilySamples {
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
      definition.accept(samples);
    }
  }

  public void addDefinition(CassandraMetricDefinition definition) {
    definitions.add(definition);
  }

  public Set<CassandraMetricDefinition> getDefinitions() {
    return definitions;
  }
}
