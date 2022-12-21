package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.k8ssandra.metrics.builder.filter.FilteringSpec;

import java.util.ArrayList;
import java.util.List;

public class Configuration {
    @JsonProperty("filters")
    private List<FilteringSpec> filters;

    @JsonProperty("port")
    private int port;

    public Configuration() {
        filters = new ArrayList<>();
    }

    public Configuration(List<FilteringSpec> filters) {
        this.filters = filters;
    }

    public List<FilteringSpec> getFilters() {
        return filters;
    }

    public int getPort() {
        return port;
    }
}
