/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.shaded.annotation.JsonProperty;
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

  @JsonProperty("extended_metrics_disabled")
  private boolean extendedDisabled;

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

  public boolean isExtendedDisabled() {
    return extendedDisabled;
  }

  public void setExtendedDisabled(boolean extendedDisabled) {
    this.extendedDisabled = extendedDisabled;
  }
}
