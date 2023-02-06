/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class LabelConfiguration {

  @JsonProperty("env")
  private Map<String, String> envVariables;

  public LabelConfiguration() {}

  public Map<String, String> getEnvVariables() {
    return envVariables;
  }
}
