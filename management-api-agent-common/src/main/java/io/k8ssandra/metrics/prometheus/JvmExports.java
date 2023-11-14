/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.prometheus;

import com.google.common.collect.Lists;
import io.k8ssandra.metrics.builder.CassandraMetricDefinition;
import io.k8ssandra.metrics.builder.CassandraMetricNameParser;
import io.k8ssandra.metrics.config.Configuration;
import io.prometheus.client.Collector;
import io.prometheus.client.hotspot.BufferPoolsExports;
import io.prometheus.client.hotspot.ClassLoadingExports;
import io.prometheus.client.hotspot.GarbageCollectorExports;
import io.prometheus.client.hotspot.MemoryAllocationExports;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import io.prometheus.client.hotspot.StandardExports;
import io.prometheus.client.hotspot.ThreadExports;
import io.prometheus.client.hotspot.VersionInfoExports;
import java.util.ArrayList;
import java.util.List;

public class JvmExports extends Collector implements Collector.Describable {

  private final CassandraMetricNameParser parser;

  private List<Collector> subCollectors;

  public JvmExports(Configuration config) {
    parser = CassandraMetricNameParser.getDefaultParser(config);
    subCollectors = new ArrayList<>(8);
    subCollectors.add(new StandardExports());
    subCollectors.add(new MemoryPoolsExports());
    subCollectors.add(new MemoryAllocationExports());
    subCollectors.add(new BufferPoolsExports());
    subCollectors.add(new GarbageCollectorExports());
    subCollectors.add(new ThreadExports());
    subCollectors.add(new ClassLoadingExports());
    subCollectors.add(new VersionInfoExports());
  }

  @Override
  public List<MetricFamilySamples> collect() {
    ArrayList<MetricFamilySamples> resultSamples = Lists.newArrayList();

    for (Collector subCollector : subCollectors) {
      List<MetricFamilySamples> familySamples = subCollector.collect();
      for (MetricFamilySamples familySample : familySamples) {
        // Recreate all the familySamples because all the fields are final and can't be replaced
        List<MetricFamilySamples.Sample> replacementSamples = new ArrayList<>();
        MetricFamilySamples replacementFamily =
            new MetricFamilySamples(
                familySample.name, familySample.type, familySample.help, replacementSamples);

        for (MetricFamilySamples.Sample sample : familySample.samples) {
          // Recreate all the samples to get the correct label names and label values
          CassandraMetricDefinition proto =
              parser.parseDropwizardMetric(sample.name, "", sample.labelNames, sample.labelValues);

          Collector.MetricFamilySamples.Sample replacementSample =
              new Collector.MetricFamilySamples.Sample(
                  proto.getMetricName(),
                  proto.getLabelNames(),
                  proto.getLabelValues(),
                  sample.value);
          replacementSamples.add(replacementSample);
        }
        resultSamples.add(replacementFamily);
      }
    }

    return resultSamples;
  }

  @Override
  public List<MetricFamilySamples> describe() {
    return new ArrayList<>();
  }
}
