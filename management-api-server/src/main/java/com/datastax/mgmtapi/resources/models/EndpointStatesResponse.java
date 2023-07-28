/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EndpointStatesResponse implements Serializable {

  @JsonProperty(value = "endpoints", required = true)
  public final List<EndpointStates> endpoints;

  @JsonCreator
  public EndpointStatesResponse(@JsonProperty("endpoints") List<EndpointStates> endpoints) {
    this.endpoints = endpoints;
  }

  public EndpointStatesResponse(Object resultObj) {
    assert resultObj instanceof List;
    final List resultObjectList = (List) resultObj;
    final List<EndpointStates> endpoints = new ArrayList<>(resultObjectList.size());
    for (Object listObj : resultObjectList) {
      assert listObj instanceof Map;
      EndpointStates epStates = new EndpointStates(listObj);
      endpoints.add(epStates);
    }
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
    final EndpointStatesResponse other = (EndpointStatesResponse) obj;
    if (!Objects.equals(this.endpoints, other.endpoints)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 83 * hash + Objects.hashCode(this.endpoints);
    return hash;
  }
}
