package io.k8ssandra.metrics.builder;

import io.prometheus.client.Collector;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CassandraMetricDefinition implements Consumer<List<Collector.MetricFamilySamples.Sample>> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CassandraMetricDefinition.class);

    private final List<String> labelNames;
    private final List<String> labelValues;
    private final String metricName;
    private Supplier<Double> valueGetter;

    private Consumer<List<Collector.MetricFamilySamples.Sample>> filler;

    public CassandraMetricDefinition(String metricName, List<String> labelNames, List<String> labelValues) {
        this.labelNames = labelNames;
        this.labelValues = labelValues;
        this.metricName = metricName;
    }

    public List<String> getLabelNames() {
        return labelNames;
    }

    public List<String> getLabelValues() {
        return labelValues;
    }

    public String getMetricName() {
        return metricName;
    }

    void setValueGetter(Supplier<Double> valueGetter) {
        this.valueGetter = valueGetter;
        this.filler = samples -> samples.add(buildSample());
    }

    void setFiller(Consumer<List<Collector.MetricFamilySamples.Sample>> filler) {
        this.filler = filler;
    }

    private Collector.MetricFamilySamples.Sample buildSample() {
        return new Collector.MetricFamilySamples.Sample(
                getMetricName(),
                getLabelNames(),
                getLabelValues(),
                valueGetter.get()
        );
    }

    @Override
    public void accept(List<Collector.MetricFamilySamples.Sample> samples) {
        this.filler.accept(samples);
    }
}
