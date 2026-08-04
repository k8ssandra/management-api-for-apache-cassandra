/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v1.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class IdentityRequest {
  @JsonProperty(value = "identity", required = true)
  public final String identity;

  @JsonCreator
  public IdentityRequest(@JsonProperty("identity") String identity) {
    this.identity = identity;
  }
}
