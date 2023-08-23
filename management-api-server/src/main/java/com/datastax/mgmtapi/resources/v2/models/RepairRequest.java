package com.datastax.mgmtapi.resources.v2.models;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RepairRequest {

  @JsonProperty(value = "keyspace", required = true)
  public final String keyspace;
  @JsonProperty(value = "tables", required = true)
  public final List<String> tables;
  @JsonProperty(value = "full_repair", defaultValue = "true")
  public final Boolean fullRepair;
  @JsonProperty(value = "associated_tokens")
  public final List<RingRange> associatedTokens;
  @JsonProperty(value = "repair_parallelism")
  public final RepairParallelism repairParallelism;
  @JsonProperty(value = "datacenters")
  public final Collection<String> datacenters;
  @JsonProperty(value = "repair_thread_count")
  public final int repairThreadCount;
  @JsonCreator
  public RepairRequest(
      @JsonProperty(value = "keyspace", required = true) String keyspace,
      @JsonProperty(value = "tables", required = true) List<String> tables,
      @JsonProperty(value = "full_repair", defaultValue = "true") Boolean fullRepair,
      @JsonProperty(value = "associated_tokens") List<RingRange> associatedTokens,
      @JsonProperty(value = "repair_parallelism") RepairParallelism repairParallelism,
      @JsonProperty(value = "datacenters") Collection<String> datacenters,
      @JsonProperty(value = "repair_thread_count") int repairThreadCount){
    this.keyspace = keyspace;
    this.tables = tables;
    this.fullRepair = fullRepair;
    this.associatedTokens = associatedTokens;
    this.datacenters = datacenters;
    this.repairParallelism = repairParallelism;
    this.repairThreadCount = repairThreadCount;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return Objects.equals(keyspace, ((RepairRequest) o).keyspace) &&
        Objects.equals(tables, ((RepairRequest) o).tables) &&
        Objects.equals(fullRepair, ((RepairRequest) o).fullRepair) &&
        Objects.equals(associatedTokens, ((RepairRequest) o).associatedTokens) &&
        Objects.equals(datacenters, ((RepairRequest) o).datacenters) &&
        Objects.equals(repairParallelism, ((RepairRequest) o).repairParallelism) &&
        Objects.equals(repairThreadCount, ((RepairRequest) o).repairThreadCount);
  }

  public int hashCode() {
    return  Objects.hashCode(keyspace) +
        Objects.hashCode(tables) +
        Objects.hashCode(fullRepair) +
        Objects.hashCode(associatedTokens) +
        Objects.hashCode(datacenters) +
        Objects.hashCode(repairParallelism) +
        Objects.hashCode(repairThreadCount);
  }
}