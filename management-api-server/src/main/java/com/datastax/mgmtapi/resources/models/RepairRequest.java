/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;


public class RepairRequest
{

    @JsonProperty(value = "keyspace_name", required = true)
    public final String keyspaceName;

    @JsonProperty(value = "tables", required = false)
    public final List<String> tables;

    @JsonProperty(value = "full", required = false)
    public final Boolean full;

    @JsonCreator
    public RepairRequest(@JsonProperty("keyspace_name") String keyspaceName, @JsonProperty("tables") List<String> tables, @JsonProperty(value = "full") Boolean full)
    {
        this.keyspaceName = keyspaceName;
        this.tables = tables;
        this.full = full == null ? Boolean.FALSE : full;
    }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 89 * hash + Objects.hashCode(this.keyspaceName);
    hash = 89 * hash + Objects.hashCode(this.tables);
    hash = 89 * hash + Objects.hashCode(this.full);
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

    final RepairRequest other = (RepairRequest) obj;
    if (!Objects.equals(this.keyspaceName, other.keyspaceName))
    {
        return false;
    }
    if (!Objects.equals(this.tables, other.tables))
    {
        return false;
    }
    if (!Objects.equals(this.full, other.full))
    {
        return false;
    }
    return true;
  }
}
