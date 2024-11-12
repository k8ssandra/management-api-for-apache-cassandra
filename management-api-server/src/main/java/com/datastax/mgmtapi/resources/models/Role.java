/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Role {

  @JsonProperty(value = "name")
  private String name;

  @JsonProperty(value = "super")
  private boolean isSuperUser;

  @JsonProperty(value = "login")
  private boolean canLogin;

  @JsonProperty(value = "datacenters")
  private String datacenters;

  @JsonProperty(value = "options")
  private String options;

  public Role(
      String name, boolean isSuperUser, boolean canLogin, String datacenters, String options) {
    this.name = name;
    this.isSuperUser = isSuperUser;
    this.canLogin = canLogin;
    this.datacenters = datacenters;
    this.options = options;
  }
}
