/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

package com.datastax.mgmtapi.resources.v2.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

public class RepairRequestResponse {
  @JsonProperty(value = "repair_id", required = true)
  public final String repairID;

  @JsonCreator
  public RepairRequestResponse(
      @JsonProperty(value = "repair_id", required = true) String repairID) {
    this.repairID = repairID;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return Objects.equals(repairID, ((RepairRequestResponse) o).repairID);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(repairID);
  }

  @Override
  public String toString() {
    try {
      return new ObjectMapper().writeValueAsString(this);
    } catch (Exception e) {
      return this.repairID;
    }
  }
}
