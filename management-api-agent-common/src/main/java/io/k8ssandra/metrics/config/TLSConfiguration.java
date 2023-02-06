/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TLSConfiguration {
  @JsonProperty("ca.crt")
  private String caCertPath;

  @JsonProperty("tls.crt")
  private String tlsCertPath;

  @JsonProperty("tls.key")
  private String tlsKeyPath;

  public TLSConfiguration() {}

  public String getCaCertPath() {
    return caCertPath;
  }

  public String getTlsCertPath() {
    return tlsCertPath;
  }

  public String getTlsKeyPath() {
    return tlsKeyPath;
  }
}
