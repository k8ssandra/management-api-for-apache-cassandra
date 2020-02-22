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

public class KeyspaceRequest implements Serializable
{
    @JsonProperty(value = "jobs", required = true)
    public final int jobs;

    @JsonProperty(value = "keyspace_name", required = true)
    public final String keyspaceName;

    @JsonProperty(value = "tables", required = true)
    public final List<String> tables;

    @JsonCreator
    public KeyspaceRequest(@JsonProperty("jobs") int jobs, @JsonProperty("keyspace_name") String keyspaceName, @JsonProperty("tables") List<String> tables)
    {
        this.jobs = jobs == 0 ? 1 : jobs;
        this.keyspaceName = keyspaceName;
        this.tables = tables;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        KeyspaceRequest that = (KeyspaceRequest) o;
        return jobs == that.jobs &&
                Objects.equals(keyspaceName, that.keyspaceName) &&
                Objects.equals(tables, that.tables);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(jobs, keyspaceName, tables);
    }
}
