/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

package com.datastax.mgmtapi.resources.v2.models;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RepairParallelism {
  SEQUENTIAL("sequential"),
  PARALLEL("parallel"),
  DATACENTER_AWARE("dc_parallel");

  private final String name;

  public static RepairParallelism fromName(String name) {
    if (PARALLEL.getName().equals(name)) {
      return PARALLEL;
    } else {
      return DATACENTER_AWARE.getName().equals(name) ? DATACENTER_AWARE : SEQUENTIAL;
    }
  }

  private RepairParallelism(String name) {
    this.name = name;
  }

  @JsonValue
  public String getName() {
    return this.name;
  }

  @Override
  public String toString() {
    return this.getName();
  }
}
