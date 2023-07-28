/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EndpointStates implements Serializable {

  @JsonProperty(value = "states", required = true)
  public final Map<String, String> states;

  @JsonCreator
  public EndpointStates(@JsonProperty("states") Map<String, String> states) {
    this.states = states;
  }

  public EndpointStates(Object obj) {
    assert obj instanceof Map;
    final Map<Object, Object> objMap = (Map) obj;
    final Map<String, String> states = new HashMap<>(objMap.size());
    for (Map.Entry entry : objMap.entrySet()) {
      states.put(entry.getKey().toString(), entry.getValue().toString());
    }
    this.states = states;
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
    final EndpointStates other = (EndpointStates) obj;
    if (!Objects.equals(this.states, other.states)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 83 * hash + Objects.hashCode(this.states);
    return hash;
  }
}
