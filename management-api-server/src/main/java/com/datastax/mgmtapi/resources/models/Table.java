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
import java.util.Map;
import java.util.Objects;

public class Table {
  @JsonProperty(value = "name", required = true)
  public final String name;

  @JsonProperty(value = "compaction")
  public final Map<String, String> compaction;

  @JsonCreator
  public Table(
      @JsonProperty("name") String name,
      @JsonProperty("compaction") Map<String, String> compaction) {
    this.name = name;
    this.compaction = compaction;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Table table = (Table) o;
    return Objects.equals(name, table.name) && Objects.equals(compaction, table.compaction);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, compaction);
  }

  @Override
  public String toString() {
    try {
      return new ObjectMapper().writeValueAsString(this);
    } catch (JsonProcessingException je) {
      return String.format("Unable to format table (%s)", je.getMessage());
    }
  }
}
