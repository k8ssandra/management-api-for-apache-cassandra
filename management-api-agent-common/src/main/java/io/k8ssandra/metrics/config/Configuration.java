package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.k8ssandra.metrics.builder.filter.RelabelSpec;

import java.util.ArrayList;
import java.util.List;

public class Configuration {

    @JsonProperty("relabels")
    private List<RelabelSpec> relabels;

    @JsonProperty("filters")
    private List<RelabelSpec> filters;

    @JsonProperty("endpoint")
    private EndpointConfiguration endpointConfiguration;

    @JsonProperty("labels")
    private LabelConfiguration labels;

    public Configuration() {
        filters = new ArrayList<>();
    }

    public Configuration(List<RelabelSpec> filters) {
        this.filters = filters;
    }

    public List<RelabelSpec> getFilters() {
        // TODO Go through relabels and only get a subset (drop/keep) ?
        return filters;
    }

    public EndpointConfiguration getEndpointConfiguration() {
        return endpointConfiguration;
    }

    public LabelConfiguration getLabels() {
        return labels;
    }

    public List<RelabelSpec> getRelabels() {
        // TODO Go through relabels and remove drop/keep ?
        return relabels;
    }
}
