/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.models;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TakeSnapshotRequest implements Serializable {

    @JsonProperty(value = "snapshot_name", required = false)
    public final String snapshotName;

    @JsonProperty(value = "keyspaces", required = false)
    public final List<String> keyspaces;

    @JsonProperty(value = "table_name", required = false)
    public final String tableName;

    @JsonProperty(value = "skip_flush", required = false)
    public final Boolean skipFlush;

    @JsonProperty(value = "keyspace_tables", required = false)
    public final List<String> keyspaceTables;

    @JsonCreator
    public TakeSnapshotRequest(
            @JsonProperty("snapshot_name") String snapshotName,
            @JsonProperty("keyspaces") List<String> keyspaces,
            @JsonProperty("table_name") String tableName,
            @JsonProperty(value = "skip_flush") Boolean skipFlush,
            @JsonProperty("keyspace_tables") List<String> keyspaceTables)
    {
        this.snapshotName = snapshotName == null ? Long.toString(System.currentTimeMillis()) : snapshotName;;
        this.keyspaces = keyspaces;
        this.tableName = tableName;
        this.skipFlush = skipFlush == null ? Boolean.FALSE : skipFlush;
        this.keyspaceTables = keyspaceTables;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + Objects.hashCode(this.snapshotName);
        hash = 83 * hash + Objects.hashCode(this.keyspaces);
        hash = 83 * hash + Objects.hashCode(this.tableName);
        hash = 83 * hash + Objects.hashCode(this.skipFlush);
        hash = 83 * hash + Objects.hashCode(this.keyspaceTables);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final TakeSnapshotRequest other = (TakeSnapshotRequest) obj;
        if (!Objects.equals(this.snapshotName, other.snapshotName))
        {
            return false;
        }
        if (!Objects.equals(this.keyspaces, other.keyspaces))
        {
            return false;
        }
        if (!Objects.equals(this.tableName, other.tableName))
        {
            return false;
        }
        if (!Objects.equals(this.skipFlush, other.skipFlush))
        {
            return false;
        }
        if (!Objects.equals(this.keyspaceTables, other.keyspaceTables))
        {
            return false;
        }
        return true;
    }

}
