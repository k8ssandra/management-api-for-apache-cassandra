package io.k8ssandra.metrics.prometheus;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import io.k8ssandra.metrics.builder.CassandraMetricRegistryListener;
import io.k8ssandra.metrics.builder.RefreshableMetricFamilySamples;
import io.k8ssandra.metrics.builder.filter.CassandraMetricDefinitionFilter;
import io.prometheus.client.Collector;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collect Dropwizard metrics from CassandraMetricRegistry. This is modified version of the Prometheus' client_java's DropwizardExports
 * to improve performance, parsing and correctness.
 */
public class CassandraDropwizardExports extends Collector implements Collector.Describable {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CassandraDropwizardExports.class);
    private final MetricRegistry registry;

    private ConcurrentHashMap<String, RefreshableMetricFamilySamples> familyCache;

    /**
     * Creates a new CassandraDropwizardExports with {@link MetricFilter#ALL}.
     *
     * @param registry a metric registry to export in prometheus.
     */
    public CassandraDropwizardExports(MetricRegistry registry) {
        this(registry, new CassandraMetricDefinitionFilter(List.of()));
    }

    /**
     * Creates a new CassandraDropwizardExports with a custom {@link MetricFilter}.
     *
     * @param registry     a metric registry to export in prometheus.
     * @param metricFilter a custom metric filter.
     */
    public CassandraDropwizardExports(MetricRegistry registry, CassandraMetricDefinitionFilter metricFilter) {
        this.registry = registry;
        this.familyCache = new ConcurrentHashMap<>();
        registry.addListener(new CassandraMetricRegistryListener(this.familyCache, metricFilter));
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

    @Override
    public List<MetricFamilySamples> describe() {
        return new ArrayList<>();
    }
}