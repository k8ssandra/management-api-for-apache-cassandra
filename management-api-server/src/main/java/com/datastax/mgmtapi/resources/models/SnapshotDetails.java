/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SnapshotDetails extends BaseEntity {

  @JsonProperty(value = "entity", required = true)
  public final List<Map<String, String>> entity;

  @JsonCreator
  public SnapshotDetails(
      @JsonProperty("entity") List<Map<String, String>> entity,
      @JsonProperty("variant") BaseEntity.Variant variant,
      @JsonProperty("annotations") List<String> annotations,
      @JsonProperty("mediaType") BaseEntity.MediaType mediaType,
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
    final SnapshotDetails other = (SnapshotDetails) obj;
    if (!Objects.equals(this.entity, other.entity)) {
      return false;
    }
    return super.equals(obj);
  }
}
