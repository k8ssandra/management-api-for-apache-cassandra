/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v1.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class IdentityToRoleRequest {
  @JsonProperty(value = "identity", required = true)
  public final String identity;

  @JsonProperty(value = "role", required = true)
  public final String role;

  @JsonCreator
  public IdentityToRoleRequest(
      @JsonProperty("identity") String identity, @JsonProperty("role") String role) {
    this.identity = identity;
    this.role = role;
  }
}
