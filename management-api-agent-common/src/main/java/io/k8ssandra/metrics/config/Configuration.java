package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.k8ssandra.metrics.builder.filter.RelabelSpec;

import java.util.ArrayList;
import java.util.List;

public class Configuration {

    @JsonProperty("relabels")
    private List<RelabelSpec> relabels;

    @JsonProperty("endpoint")
    private EndpointConfiguration endpointConfiguration;

    @JsonProperty("labels")
    private LabelConfiguration labels;

    public Configuration() {
        relabels = new ArrayList<>();
    }

    public EndpointConfiguration getEndpointConfiguration() {
        return endpointConfiguration;
    }

    public LabelConfiguration getLabels() {
        return labels;
    }

    public List<RelabelSpec> getRelabels() {
        return relabels;
    }

    public void setRelabels(List<RelabelSpec> relabels) {
        this.relabels = relabels;
    }
}
