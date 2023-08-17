/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EndpointStates extends BaseEntity {

  @JsonProperty(value = "entity", required = true)
  public final List<Map<String, String>> entity;

  @JsonCreator
  public EndpointStates(
      @JsonProperty("entity") List<Map<String, String>> entity,
      @JsonProperty("variant") Variant variant,
      @JsonProperty("annotations") List<String> annotations,
      @JsonProperty("mediaType") MediaType mediaType,
      @JsonProperty("language") String language,
      @JsonProperty("encoding") String encoding) {
    super(variant, annotations, mediaType, language, encoding);
    this.entity = entity;
  }

  @Override
  public int hashCode() {
    int hash = super.hashCode();
    hash = 83 * hash + Objects.hashCode(this.entity);
    return hash;
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
    if (!Objects.equals(this.entity, other.entity)) {
      return false;
    }
    return super.equals(obj);
  }

  @Override
  public String toString() {
    try {
      return new ObjectMapper().writeValueAsString(this);
    } catch (JsonProcessingException je) {
      return "Unable to parse endpoint states into an entity";
    }
  }
}
