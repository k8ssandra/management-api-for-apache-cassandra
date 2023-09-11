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

public class TokenRangeToEndpointResponse {

  @JsonProperty(value = "token_range_to_endpoints", required = true)
  public final List<TokenRangeToEndpoints> tokenRangeToEndpoints;

  @JsonCreator
  public TokenRangeToEndpointResponse(
      @JsonProperty(value = "token_range_to_endpoints", required = true)
          List<TokenRangeToEndpoints> list) {
    this.tokenRangeToEndpoints = list;
  }

  @Override
  public int hashCode() {
    return 83 * Objects.hashCode(tokenRangeToEndpoints);
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
    TokenRangeToEndpointResponse other = (TokenRangeToEndpointResponse) obj;
    return Objects.equals(this.tokenRangeToEndpoints, other.tokenRangeToEndpoints);
  }

  @Override
  public String toString() {
    try {
      return new ObjectMapper().writeValueAsString(this);
    } catch (JsonProcessingException e) {
      return String.format("Unable to format TokenRangeToEndpointResponse (%s)", e.getMessage());
    }
  }
}
