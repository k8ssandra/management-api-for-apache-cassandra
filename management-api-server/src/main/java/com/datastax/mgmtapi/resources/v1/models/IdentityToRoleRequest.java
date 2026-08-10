/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v1.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public class IdentityToRoleRequest {
  @JsonProperty(value = "identity", required = true)
  public final String identity;

  @JsonProperty(value = "role", required = true)
  public final String role;

  @JsonProperty("ttl")
  @Schema(description = "Time-to-live in seconds")
  public final Integer ttl;

  @JsonCreator
  public IdentityToRoleRequest(
      @JsonProperty("identity") String identity,
      @JsonProperty("role") String role,
      @JsonProperty("ttl") Integer ttl) {
    this.identity = identity;
    this.role = role;
    this.ttl = ttl;
  }
}
