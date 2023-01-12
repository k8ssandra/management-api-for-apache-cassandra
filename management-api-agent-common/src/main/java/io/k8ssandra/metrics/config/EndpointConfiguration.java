package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EndpointConfiguration {

    @JsonProperty("port")
    private int port;

    @JsonProperty("address")
    private String host;

    @JsonProperty("tls")
    private TLSConfiguration tlsConfig;

    public EndpointConfiguration() {

    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public TLSConfiguration getTlsConfig() {
        return tlsConfig;
    }
}
