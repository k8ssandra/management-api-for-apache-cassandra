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

/**
 * Describes the state of an active compaction running on the server.
 *
 * <p>Some fields are specific to certain Cassandra versions, this is indicated in their comment.
 */
public class Compaction {

  // Note: for simplicity, we use the same keys in our JSON payload as the map returned by
  // Cassandra. These constants are used for both, do not change them or the corresponding JSON
  // fields will always be empty.
  private static final String ID_KEY = "id";
  private static final String KEYSPACE_KEY = "keyspace";
  private static final String COLUMN_FAMILY_KEY = "columnfamily";
  private static final String COMPLETED_KEY = "completed";
  private static final String TOTAL_KEY = "total";
  private static final String TASK_TYPE_KEY = "taskType";
  private static final String UNIT_KEY = "unit";
  private static final String COMPACTION_ID_KEY = "compactionId";
  private static final String SSTABLES_KEY = "sstables";
  private static final String TARGET_DIRECTORY_KEY = "targetDirectory";
  private static final String OPERATION_TYPE_KEY = "operationType";
  private static final String OPERATION_ID_KEY = "operationId";
  private static final String DESCRIPTION_KEY = "description";

  @JsonProperty(ID_KEY)
  public final String id;

  @JsonProperty(KEYSPACE_KEY)
  public final String keyspace;

  @JsonProperty(COLUMN_FAMILY_KEY)
  public final String columnFamily;

  @JsonProperty(COMPLETED_KEY)
  public final Long completed;

  @JsonProperty(TOTAL_KEY)
  public final Long total;

  /** Only present in OSS Cassandra. */
  @JsonProperty(TASK_TYPE_KEY)
  public final String taskType;

  @JsonProperty(UNIT_KEY)
  public final String unit;

  /** Only present in OSS Cassandra. */
  @JsonProperty(COMPACTION_ID_KEY)
  public final String compactionId;

  /** Only present in OSS Cassandra 4 or above. */
  @JsonProperty(SSTABLES_KEY)
  public final String ssTables;

  /** Only present in OSS Cassandra 5. */
  @JsonProperty(TARGET_DIRECTORY_KEY)
  public final String targetDirectory;

  /** Only present in DSE. */
  @JsonProperty(OPERATION_TYPE_KEY)
  public final String operationType;

  /** Only present in DSE. */
  @JsonProperty(OPERATION_ID_KEY)
  public final String operationId;

  /** Only present in DSE 6.8. */
  @JsonProperty(DESCRIPTION_KEY)
  public final String description;

  @JsonCreator
  public Compaction(
      @JsonProperty(ID_KEY) String id,
      @JsonProperty(KEYSPACE_KEY) String keyspace,
      @JsonProperty(COLUMN_FAMILY_KEY) String columnFamily,
      @JsonProperty(COMPLETED_KEY) Long completed,
      @JsonProperty(TOTAL_KEY) Long total,
      @JsonProperty(TASK_TYPE_KEY) String taskType,
      @JsonProperty(UNIT_KEY) String unit,
      @JsonProperty(COMPACTION_ID_KEY) String compactionId,
      @JsonProperty(SSTABLES_KEY) String ssTables,
      @JsonProperty(TARGET_DIRECTORY_KEY) String targetDirectory,
      @JsonProperty(OPERATION_TYPE_KEY) String operationType,
      @JsonProperty(OPERATION_ID_KEY) String operationId,
      @JsonProperty(DESCRIPTION_KEY) String description) {

    this.id = id;
    this.keyspace = keyspace;
    this.columnFamily = columnFamily;
    this.completed = completed;
    this.total = total;
    this.taskType = taskType;
    this.unit = unit;
    this.compactionId = compactionId;
    this.ssTables = ssTables;
    this.targetDirectory = targetDirectory;
    this.operationId = operationId;
    this.operationType = operationType;
    this.description = description;
  }

  public static Compaction fromMap(Map<String, String> m) {
    return new Compaction(
        m.get(ID_KEY),
        m.get(KEYSPACE_KEY),
        m.get(COLUMN_FAMILY_KEY),
        parseLongOrNull(m.get(COMPLETED_KEY)),
        parseLongOrNull(m.get(TOTAL_KEY)),
        m.get(TASK_TYPE_KEY),
        m.get(UNIT_KEY),
        m.get(COMPACTION_ID_KEY),
        m.get(SSTABLES_KEY),
        m.get(TARGET_DIRECTORY_KEY),
        m.get(OPERATION_TYPE_KEY),
        m.get(OPERATION_ID_KEY),
        m.get(DESCRIPTION_KEY));
  }

  private static Long parseLongOrNull(String s) {
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Compaction that = (Compaction) o;
    return Objects.equals(id, that.id)
        && Objects.equals(keyspace, that.keyspace)
        && Objects.equals(columnFamily, that.columnFamily)
        && Objects.equals(completed, that.completed)
        && Objects.equals(total, that.total)
        && Objects.equals(taskType, that.taskType)
        && Objects.equals(unit, that.unit)
        && Objects.equals(compactionId, that.compactionId)
        && Objects.equals(ssTables, that.ssTables)
        && Objects.equals(targetDirectory, that.targetDirectory)
        && Objects.equals(operationType, that.operationType)
        && Objects.equals(operationId, that.operationId)
        && Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        keyspace,
        columnFamily,
        completed,
        total,
        taskType,
        unit,
        compactionId,
        ssTables,
        targetDirectory,
        operationType,
        operationId,
        description);
  }

  @Override
  public String toString() {
    try {
      return new ObjectMapper().writeValueAsString(this);
    } catch (JsonProcessingException je) {
      return String.format("Unable to format compaction (%s)", je.getMessage());
    }
  }
}
