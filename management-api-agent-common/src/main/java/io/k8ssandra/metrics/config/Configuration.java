/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.k8ssandra.metrics.builder.relabel.RelabelSpec;
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

  public void setLabels(LabelConfiguration labels) {
    this.labels = labels;
  }
}
