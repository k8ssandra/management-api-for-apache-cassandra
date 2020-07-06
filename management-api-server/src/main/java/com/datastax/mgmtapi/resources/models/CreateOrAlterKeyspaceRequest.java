package com.datastax.mgmtapi.resources.models;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateOrAlterKeyspaceRequest implements Serializable
{
    @JsonProperty(value = "keyspace_name", required = true)
    public final String keyspaceName;

    @JsonProperty(value = "replication_settings", required = true)
    public final List<ReplicationSetting> replicationSettings;

    @JsonCreator
    public CreateOrAlterKeyspaceRequest(@JsonProperty("keyspace_name") String keyspaceName, @JsonProperty("replication_settings") List<ReplicationSetting> replicationSettings)
    {
        this.keyspaceName = keyspaceName;
        this.replicationSettings = replicationSettings;
    }

    public Map<String, Integer> replicationSettingsAsMap()
    {
        Map<String, Integer> result = new HashMap<>(replicationSettings.size());
        replicationSettings.forEach(r -> result.put(r.dcName, r.replicationFactor));
        return result;
    }
}
