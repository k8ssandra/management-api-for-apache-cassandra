package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.k8ssandra.metrics.builder.filter.FilteringSpec;

import java.util.ArrayList;
import java.util.List;

public class Configuration {
    @JsonProperty("filters")
    private List<FilteringSpec> filters;

    @JsonProperty("endpoint")
    private EndpointConfiguration endpointConfiguration;

    @JsonProperty("labels")
    private LabelConfiguration labels;

    public Configuration() {
        filters = new ArrayList<>();
    }

    public Configuration(List<FilteringSpec> filters) {
        this.filters = filters;
    }

    public List<FilteringSpec> getFilters() {
        return filters;
    }

    public EndpointConfiguration getEndpointConfiguration() {
        return endpointConfiguration;
    }

    public LabelConfiguration getLabels() {
        return labels;
    }
}
