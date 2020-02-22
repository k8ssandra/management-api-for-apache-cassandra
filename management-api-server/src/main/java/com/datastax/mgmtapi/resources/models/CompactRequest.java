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

public class CompactRequest implements Serializable
{
    @JsonProperty(value = "split_output", required = true)
    public final boolean splitOutput;

    @JsonProperty(value = "user_defined", required = true)
    public final boolean userDefined;

    @JsonProperty(value = "start_token")
    public final String startToken;

    @JsonProperty(value = "end_token")
    public final String endToken;

    @JsonProperty(value = "keyspace_name", required = true)
    public final String keyspaceName;

    @JsonProperty(value = "user_defined_files")
    public final List<String> userDefinedFiles;

    @JsonProperty(value = "tables")
    public final List<String> tables;

    @JsonCreator
    public CompactRequest(@JsonProperty("split_output") boolean splitOutput, @JsonProperty("user_defined") boolean userDefined,
                          @JsonProperty("start_token") String startToken, @JsonProperty("end_token") String endToken,
                          @JsonProperty("keyspace_name") String keyspaceName, @JsonProperty("user_defined_files") List<String> userDefinedFiles,
                          @JsonProperty("tables") List<String> tables)
    {

        this.splitOutput = splitOutput;
        this.userDefined = userDefined;
        this.startToken = startToken;
        this.endToken = endToken;
        this.keyspaceName = keyspaceName;
        this.userDefinedFiles = userDefinedFiles;
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
        CompactRequest that = (CompactRequest) o;
        return splitOutput == that.splitOutput &&
                userDefined == that.userDefined &&
                Objects.equals(startToken, that.startToken) &&
                Objects.equals(endToken, that.endToken) &&
                Objects.equals(keyspaceName, that.keyspaceName) &&
                Objects.equals(userDefinedFiles, that.userDefinedFiles) &&
                Objects.equals(tables, that.tables);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(splitOutput, userDefined, startToken, endToken, keyspaceName, userDefinedFiles, tables);
    }
}
