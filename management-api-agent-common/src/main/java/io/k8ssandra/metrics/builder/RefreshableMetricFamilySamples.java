package io.k8ssandra.metrics.builder;

import io.prometheus.client.Collector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RefreshableMetricFamilySamples extends Collector.MetricFamilySamples {
    private final List<CassandraMetricDefinition> definitions;

    public RefreshableMetricFamilySamples(String name, Collector.Type type, String help, List<Sample> samples) {
        super(name, type, help, samples);
        definitions = new CopyOnWriteArrayList<>();
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

    public List<CassandraMetricDefinition> getDefinitions() {
        return definitions;
    }
}
