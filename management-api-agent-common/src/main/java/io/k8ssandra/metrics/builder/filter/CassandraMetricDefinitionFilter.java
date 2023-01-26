package io.k8ssandra.metrics.builder.filter;

import io.k8ssandra.metrics.builder.CassandraMetricDefinition;

import java.util.*;

/**
 * CassandraMetricDefinitionFilter takes all the relabel rules with action "drop" or "keep" and processes them.
 */
public class CassandraMetricDefinitionFilter {

    private final List<RelabelSpec> relabelSpecs = new ArrayList<>();

    public CassandraMetricDefinitionFilter(List<RelabelSpec> filters) {
        for (RelabelSpec filter : filters) {
            if(filter.getAction() == RelabelSpec.Action.drop || filter.getAction() == RelabelSpec.Action.keep) {
                relabelSpecs.add(filter);
            }
        }
    }

    public boolean matches(CassandraMetricDefinition definition, String dropwizardName) {
        boolean keep = true;
        if(relabelSpecs.size() > 0) {
            HashMap<String, String> labels = getLabels(definition, dropwizardName);
            for (RelabelSpec filter : relabelSpecs) {
                keep &= this.filter(filter, labels);
            }
        }

        return keep;
    }

    private boolean filter(RelabelSpec filter, Map<String, String> labels) {
        String separator = filter.getSeparator();
        if(separator == null) {
            separator = RelabelSpec.DEFAULT_SEPARATOR;
        }
        StringJoiner joiner = new StringJoiner(separator);
        for (String sourceLabel : filter.getSourceLabels()) {
            String labelValue = labels.get(sourceLabel);
            if (labelValue == null) {
                labelValue = "";
            }
            joiner.add(labelValue);
        }

        String value = joiner.toString();
        boolean match = filter.getRegexp().matcher(value).matches();

        if (filter.getAction() == RelabelSpec.Action.drop) {
            return !match;
        }

        return match;
    }

    private HashMap<String, String> getLabels(CassandraMetricDefinition definition, String dropwizardName) {
        HashMap<String, String> labels = new HashMap<>(definition.getLabelValues().size());
        for(int i = 0; i < definition.getLabelValues().size(); i++) {
            labels.put(definition.getLabelNames().get(i), definition.getLabelValues().get(i));
        }

        labels.put(RelabelSpec.METRIC_NAME_LABELNAME, definition.getMetricName());
        labels.put(RelabelSpec.CASSANDRA_METRIC_NAME_LABELNAME, dropwizardName);

        return labels;
    }
}
