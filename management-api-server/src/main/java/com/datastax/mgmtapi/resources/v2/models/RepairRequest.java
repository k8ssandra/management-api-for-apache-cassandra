/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.v2.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

public class RepairRequest {

  @JsonProperty(value = "keyspace", required = true)
  public final String keyspace;

  @Nullable
  @JsonProperty(value = "tables")
  public final List<String> tables;

  @JsonProperty(value = "full_repair", defaultValue = "true")
  public final Boolean fullRepair;

  @Nullable
  @JsonProperty(value = "associated_tokens")
  public final List<RingRange> associatedTokens;

  @Nullable
  @JsonProperty(value = "repair_parallelism")
  public final RepairParallelism repairParallelism;

  @Nullable
  @JsonProperty(value = "datacenters")
  public final List<String> datacenters;

  @Nullable
  @JsonProperty(value = "repair_thread_count")
  public final Integer repairThreadCount;

  @JsonCreator
  public RepairRequest(
      @JsonProperty(value = "keyspace", required = true) String keyspace,
      @JsonProperty(value = "tables") List<String> tables,
      @JsonProperty(value = "full_repair", defaultValue = "true") Boolean fullRepair,
      @JsonProperty(value = "associated_tokens") List<RingRange> associatedTokens,
      @JsonProperty(value = "repair_parallelism") RepairParallelism repairParallelism,
      @JsonProperty(value = "datacenters") List<String> datacenters,
      @JsonProperty(value = "repair_thread_count") Integer repairThreadCount) {
    this.keyspace = keyspace;
    this.tables = tables;
    this.fullRepair = fullRepair;
    this.associatedTokens = associatedTokens;
    this.datacenters = datacenters;
    this.repairParallelism = repairParallelism;
    this.repairThreadCount = repairThreadCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RepairRequest other = (RepairRequest) o;
    return Objects.equals(keyspace, other.keyspace)
        && Objects.equals(tables, other.tables)
        && Objects.equals(fullRepair, other.fullRepair)
        && Objects.equals(associatedTokens, other.associatedTokens)
        && Objects.equals(datacenters, other.datacenters)
        && Objects.equals(repairParallelism, other.repairParallelism)
        && Objects.equals(repairThreadCount, other.repairThreadCount);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(keyspace)
        + Objects.hashCode(tables)
        + Objects.hashCode(fullRepair)
        + Objects.hashCode(associatedTokens)
        + Objects.hashCode(datacenters)
        + Objects.hashCode(repairParallelism)
        + Objects.hashCode(repairThreadCount);
  }
}
