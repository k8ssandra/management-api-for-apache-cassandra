/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v2.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;

public class TokenRangeToEndpoints {

  @JsonProperty(value = "tokens", required = true)
  public final List<Long> tokens;

  @JsonProperty(value = "endpoints", required = true)
  public final List<String> endpoints;

  @JsonCreator
  public TokenRangeToEndpoints(
      @JsonProperty(value = "tokens", required = true) List<Long> tokens,
      @JsonProperty(value = "endpoints", required = true) List<String> endpoints) {
    this.tokens = tokens;
    this.endpoints = endpoints;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    TokenRangeToEndpoints other = (TokenRangeToEndpoints) obj;
    if (!Objects.equals(this.tokens, other.tokens)) {
      return false;
    }
    return Objects.equals(this.endpoints, other.endpoints);
  }

  @Override
  public int hashCode() {
    return 83 * Objects.hashCode(this.tokens) * Objects.hashCode(this.endpoints);
  }

  @Override
  public String toString() {
    try {
      return new ObjectMapper().writeValueAsString(this);
    } catch (JsonProcessingException e) {
      return String.format("Unable to format TokenRangeToEndpoints (%s)", e.getMessage());
    }
  }
}
