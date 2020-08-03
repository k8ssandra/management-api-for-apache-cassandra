package com.datastax.mgmtapi.resources.models;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReplicationSetting implements Serializable
{
    @JsonProperty(value = "dc_name", required = true)
    public final String dcName;

    @JsonProperty(value = "replication_factor", required = true)
    public final int replicationFactor;

    @JsonCreator
    public ReplicationSetting(@JsonProperty("dc_name") String dcName, @JsonProperty("replication_factor") int replicationFactor)
    {
        this.dcName = dcName;
        this.replicationFactor = replicationFactor;
    }
}
