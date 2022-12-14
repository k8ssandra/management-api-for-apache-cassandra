package io.k8ssandra.metrics.builder;

import com.codahale.metrics.*;
import io.k8ssandra.metrics.builder.filter.CassandraMetricDefinitionFilter;
import io.prometheus.client.Collector;
import org.apache.cassandra.utils.EstimatedHistogram;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.k8ssandra.metrics.builder.CassandraMetricsTools.PRECOMPUTED_QUANTILES;
import static io.k8ssandra.metrics.builder.CassandraMetricsTools.PRECOMPUTED_QUANTILES_TEXT;

public class CassandraMetricRegistryListener implements MetricRegistryListener {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CassandraMetricRegistryListener.class);

    private final CassandraMetricNameParser parser;

    private final CassandraMetricDefinitionFilter metricFilter;

    private final ConcurrentHashMap<String, RefreshableMetricFamilySamples> familyCache;

    // This cache is used for the remove purpose, we need dropwizardName -> metricName mapping
    private final ConcurrentHashMap<String, String> cache;

    public CassandraMetricRegistryListener(ConcurrentHashMap<String, RefreshableMetricFamilySamples> familyCache, CassandraMetricDefinitionFilter metricFilter) {
        parser = new CassandraMetricNameParser(CassandraMetricsTools.DEFAULT_LABEL_NAMES, CassandraMetricsTools.DEFAULT_LABEL_VALUES);
        cache = new ConcurrentHashMap<>();
        this.familyCache = familyCache;
        this.metricFilter = metricFilter;
    }

    public void updateCache(String dropwizardName, String metricName, RefreshableMetricFamilySamples prototype) {
        prototype.getDefinitions().removeIf(next -> !metricFilter.matches(next, dropwizardName));

        if (prototype.getDefinitions().size() < 1) {
            return;
        }

        RefreshableMetricFamilySamples familySamples;
        if (!familyCache.containsKey(metricName)) {
            familyCache.put(metricName, prototype);
            cache.put(dropwizardName, metricName);
        } else {
            familySamples = familyCache.get(metricName);
            prototype.getDefinitions().forEach(familySamples::addDefinition);
        }
    }

    public void removeFromCache(String dropwizardName) {
        String metricName = cache.get(dropwizardName);
        if(metricName == null) {
            return;
        }

        RefreshableMetricFamilySamples familySampler = familyCache.get(metricName);

        familySampler.getDefinitions().removeIf(cmd -> cmd.getMetricName().equals(metricName) ||
                cmd.getMetricName().equals(metricName + "_count") ||
                cmd.getMetricName().equals(metricName + "_total"));

        if(familySampler.getDefinitions().size() == 0) {
            this.familyCache.remove(metricName);
            cache.remove(dropwizardName);
        }
    }

    private void setGaugeHistogramFiller(Gauge gauge, CassandraMetricDefinition proto) {
        proto.setFiller((samples) -> {
            if(gauge.getValue() == null) {
                return;
            }
            long[] inputValues = (long[]) gauge.getValue();
            if (inputValues.length == 0) {
                // Empty
                return;
            }

            final EstimatedHistogram hist = new EstimatedHistogram(inputValues);
            for(int i = 0; i < PRECOMPUTED_QUANTILES.length; i++) {
                List<String> labelValues = new ArrayList<>(proto.getLabelValues().size() + 1);
                int j = 0;
                for(; j < proto.getLabelValues().size(); j++) {
                    labelValues.add(j, proto.getLabelValues().get(j));
                }
                labelValues.add(j, PRECOMPUTED_QUANTILES_TEXT[i]);
                Collector.MetricFamilySamples.Sample sample = new Collector.MetricFamilySamples.Sample(
                        proto.getMetricName(),
                        proto.getLabelNames(),
                        labelValues,
                        hist.percentile(PRECOMPUTED_QUANTILES[i]));
                samples.add(sample);
            }
        });
    }

    private Supplier<Double> fromGauge(final Gauge<?> gauge) {
        return () -> {
            Object obj = gauge.getValue();
            double value;
            if (obj instanceof Number) {
                value = ((Number) obj).doubleValue();
            } else if (obj instanceof Boolean) {
                value = ((Boolean) obj) ? 1 : 0;
            } else {
                // These are of type "HashMap<?, ?> and ArrayList<?>", but I haven't found any with actual data on my tests. Add specific parsing
                // later if we find out something valuable is missing.
                return 0.0;
            }

            return value;
        };
    }

    @Override
    public void onGaugeAdded(String dropwizardName, Gauge<?> gauge) {
        if(gauge.getValue() instanceof long[]) {
            // Treat this as a histogram, not gauge
            final CassandraMetricDefinition proto = parser.parseDropwizardMetric(dropwizardName, "", List.of("quantile"), new ArrayList<>());
            final CassandraMetricDefinition count = parser.parseDropwizardMetric(dropwizardName, "_count", new ArrayList<>(), new ArrayList<>());

            setGaugeHistogramFiller(gauge, proto);

            count.setValueGetter(() -> {
                if (gauge.getValue() == null) {
                    return 0.0;
                }
                long[] inputValues = (long[]) gauge.getValue();
                if (inputValues.length == 0) {
                    // Empty
                    return 0.0;
                }

                // _count
                final EstimatedHistogram hist = new EstimatedHistogram(inputValues);
                return (double) hist.count();
            });

            RefreshableMetricFamilySamples familySamples = new RefreshableMetricFamilySamples(proto.getMetricName(), Collector.Type.SUMMARY, "", new ArrayList<>());
            familySamples.addDefinition(proto);
            familySamples.addDefinition(count);

            updateCache(dropwizardName, proto.getMetricName(), familySamples);
            return;
        }
        Supplier<Double> gaugeSupplier = fromGauge(gauge);
        CassandraMetricDefinition sample = parser.parseDropwizardMetric(dropwizardName, "", new ArrayList<>(), new ArrayList<>());
        sample.setValueGetter(gaugeSupplier);
        RefreshableMetricFamilySamples familySamples = new RefreshableMetricFamilySamples(sample.getMetricName(), Collector.Type.GAUGE, "", new ArrayList<>());
        familySamples.addDefinition(sample);
        updateCache(dropwizardName, sample.getMetricName(), familySamples);
    }

    @Override
    public void onGaugeRemoved(String name) {
        removeFromCache(name);
    }

    @Override
    public void onCounterAdded(String name, Counter counter) {
        Supplier<Double> getValue = () -> (double) counter.getCount();
        CassandraMetricDefinition sampler = parser.parseDropwizardMetric(name, "", new ArrayList<>(), new ArrayList<>());
        sampler.setValueGetter(getValue);
        RefreshableMetricFamilySamples familySamples = new RefreshableMetricFamilySamples(sampler.getMetricName(), Collector.Type.GAUGE, "", new ArrayList<>());
        familySamples.addDefinition(sampler);
        updateCache(name, sampler.getMetricName(), familySamples);
    }

    @Override
    public void onCounterRemoved(String name) {
        removeFromCache(name);
    }

    @Override
    public void onHistogramAdded(String dropwizardName, Histogram histogram) {
        // TODO Do we want extra processing for DecayingHistogram and EstimatedHistograms?

        final CassandraMetricDefinition proto = parser.parseDropwizardMetric(dropwizardName, "", List.of("quantile"), new ArrayList<>());
        final CassandraMetricDefinition count = parser.parseDropwizardMetric(dropwizardName, "_count", new ArrayList<>(), new ArrayList<>());
        Supplier<Double> countSupplier = () -> (double) histogram.getCount();

        RefreshableMetricFamilySamples familySamples = new RefreshableMetricFamilySamples(proto.getMetricName(), Collector.Type.SUMMARY, "", new ArrayList<>());
        setHistogramFiller(histogram, proto, 1.0);
        count.setValueGetter(countSupplier);
        familySamples.addDefinition(proto);
        familySamples.addDefinition(count);

        updateCache(dropwizardName, proto.getMetricName(), familySamples);
    }

    private static void setHistogramFiller(Histogram histogram, CassandraMetricDefinition proto, double factor) {
        proto.setFiller((samples) -> {
            Snapshot snapshot = histogram.getSnapshot();
            double[] values = new double[]{
                    snapshot.getMedian(),
                    snapshot.get75thPercentile(),
                    snapshot.get95thPercentile(),
                    snapshot.get98thPercentile(),
                    snapshot.get99thPercentile(),
                    snapshot.get999thPercentile()
            };
            for(int i = 0; i < PRECOMPUTED_QUANTILES.length; i++) {
                List<String> labelValues = new ArrayList<>(proto.getLabelValues().size() + 1);
                int j = 0;
                for(; j < proto.getLabelValues().size(); j++) {
                    labelValues.add(j, proto.getLabelValues().get(j));
                }
                labelValues.add(j, PRECOMPUTED_QUANTILES_TEXT[i]);
                Collector.MetricFamilySamples.Sample sample = new Collector.MetricFamilySamples.Sample(
                        proto.getMetricName(),
                        proto.getLabelNames(),
                        labelValues,
                        values[i] * factor);
                samples.add(sample);
            }
        });
    }

    @Override
    public void onHistogramRemoved(String dropwizardName) {
        removeFromCache(dropwizardName);
    }

    @Override
    public void onMeterAdded(String name, Meter meter) {
        Supplier<Double> getValue = () -> (double) meter.getCount();
        CassandraMetricDefinition total = parser.parseDropwizardMetric(name, "_total", new ArrayList<>(), new ArrayList<>());
        total.setValueGetter(getValue);

        RefreshableMetricFamilySamples familySamples = new RefreshableMetricFamilySamples(total.getMetricName(), Collector.Type.COUNTER, "", new ArrayList<>());
        familySamples.addDefinition(total);
        updateCache(name, total.getMetricName(), familySamples);
    }

    @Override
    public void onMeterRemoved(String name) {
        removeFromCache(name);
    }

    private void setTimerFiller(Timer timer, CassandraMetricDefinition proto, double factor) {
        proto.setFiller((samples) -> {
            Snapshot snapshot = timer.getSnapshot();
            double[] values = new double[]{
                    snapshot.getMedian(),
                    snapshot.get75thPercentile(),
                    snapshot.get95thPercentile(),
                    snapshot.get98thPercentile(),
                    snapshot.get99thPercentile(),
                    snapshot.get999thPercentile()
            };
            for(int i = 0; i < PRECOMPUTED_QUANTILES.length; i++) {
                List<String> labelValues = new ArrayList<>(proto.getLabelValues().size() + 1);
                int j = 0;
                for(; j < proto.getLabelValues().size(); j++) {
                    labelValues.add(j, proto.getLabelValues().get(j));
                }
                labelValues.add(j, PRECOMPUTED_QUANTILES_TEXT[i]);
                Collector.MetricFamilySamples.Sample sample = new Collector.MetricFamilySamples.Sample(
                        proto.getMetricName(),
                        proto.getLabelNames(),
                        labelValues,
                        values[i] * factor);
                samples.add(sample);
            }
        });
    }

    @Override
    public void onTimerAdded(String dropwizardName, Timer timer) {
        double factor = 1.0D / TimeUnit.SECONDS.toNanos(1L);
        final CassandraMetricDefinition proto = parser.parseDropwizardMetric(dropwizardName, "", List.of("quantile"), new ArrayList<>());
        final CassandraMetricDefinition count = parser.parseDropwizardMetric(dropwizardName, "_count", new ArrayList<>(), new ArrayList<>());
        Supplier<Double> getValue = () -> (double) timer.getCount();

        count.setValueGetter(getValue);
        setTimerFiller(timer, proto, factor);

        RefreshableMetricFamilySamples familySamples = new RefreshableMetricFamilySamples(proto.getMetricName(), Collector.Type.SUMMARY, "", new ArrayList<>());
        familySamples.addDefinition(proto);
        familySamples.addDefinition(count);

        updateCache(dropwizardName, proto.getMetricName(), familySamples);
    }

    @Override
    public void onTimerRemoved(String name) {
        onHistogramRemoved(name);
    }
}
