package io.k8ssandra.metrics.builder.filter;

import io.k8ssandra.metrics.builder.CassandraMetricDefinition;

import java.util.HashMap;
import java.util.List;

public class CassandraMetricDefinitionFilter {

    private List<FilteringSpec> filteringSpecs;

    public CassandraMetricDefinitionFilter(List<FilteringSpec> filters) {
        this.filteringSpecs = filters;
    }

    public boolean matches(CassandraMetricDefinition definition, String dropwizardName) {
        boolean keep = true;
        if(filteringSpecs.size() > 0) {
            HashMap<String, String> labels = getLabels(definition, dropwizardName);
            for (FilteringSpec filter : filteringSpecs) {
                keep &= filter.filter(labels);
            }
        }

        return keep;
    }

    private HashMap<String, String> getLabels(CassandraMetricDefinition definition, String dropwizardName) {
        HashMap<String, String> labels = new HashMap<>(definition.getLabelValues().size());
        for(int i = 0; i < definition.getLabelValues().size(); i++) {
            labels.put(definition.getLabelNames().get(i), definition.getLabelValues().get(i));
        }

        labels.put(FilteringSpec.METRIC_NAME_LABELNAME, definition.getMetricName());
        labels.put(FilteringSpec.CASSANDRA_METRIC_NAME_LABELNAME, dropwizardName);

        return labels;
    }
}
