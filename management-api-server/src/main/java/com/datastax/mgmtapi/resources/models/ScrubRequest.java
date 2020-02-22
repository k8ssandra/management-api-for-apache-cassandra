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

public class ScrubRequest implements Serializable
{
    @JsonProperty(value = "disable_snapshot", required = true)
    public final boolean disableSnapshot;

    @JsonProperty(value = "skip_corrupted", required = true)
    public final boolean skipCorrupted;

    @JsonProperty(value = "check_data", required = true)
    public final boolean checkData;

    @JsonProperty(value = "reinsert_overflowed_ttl", required = true)
    public final boolean reinsertOverflowedTTL;

    @JsonProperty(value = "jobs", required = true)
    public final int jobs;

    @JsonProperty(value = "keyspace_name", required = true)
    public final String keyspaceName;

    @JsonProperty(value = "tables", required = true)
    public final List<String> tables;

    @JsonCreator
    public ScrubRequest(@JsonProperty("disable_snapshot") boolean disableSnapshot, @JsonProperty("skip_corrupted") boolean skipCorrupted,
                        @JsonProperty("check_data") boolean checkData, @JsonProperty("reinsert_overflowed_ttl") boolean reinsertOverflowedTTL,
                        @JsonProperty("jobs") int jobs, @JsonProperty("keyspace_name") String keyspaceName, @JsonProperty("tables") List<String> tables)
    {
        this.disableSnapshot = disableSnapshot;
        this.skipCorrupted = skipCorrupted;
        this.checkData = checkData;
        this.reinsertOverflowedTTL = reinsertOverflowedTTL;
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
        ScrubRequest that = (ScrubRequest) o;
        return disableSnapshot == that.disableSnapshot &&
                skipCorrupted == that.skipCorrupted &&
                checkData == that.checkData &&
                reinsertOverflowedTTL == that.reinsertOverflowedTTL &&
                jobs == that.jobs &&
                Objects.equals(keyspaceName, that.keyspaceName) &&
                Objects.equals(tables, that.tables);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(disableSnapshot, skipCorrupted, checkData, reinsertOverflowedTTL, jobs, keyspaceName, tables);
    }
}
