/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

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
}
