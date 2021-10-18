package com.datastax.mgmtapi.resources.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class FeatureSet implements Serializable {

    public enum Feature {
        ASYNC_SSTABLE_TASKS("async_sstable_tasks"),
        FULL_QUERY_LOGGING("full_query_logging");

        @JsonValue
        private String featureName;

        Feature(String name) {
            this.featureName = name;
        }
    }
    

    @JsonProperty(value = "cassandra_version")
    private String cassandraVersion;

    @JsonProperty(value = "mgmt_version")
    private String mgmtVersion;

    @JsonProperty(value = "features")
    private List<Feature> features;

    /**
     * Simplified method to return all Features as the available list
     */
    public FeatureSet(String cassandraVersion, String managementApiVersion) {
        this(cassandraVersion, managementApiVersion, Arrays.asList(Feature.values()));
    }

    @JsonCreator
    public FeatureSet(@JsonProperty(value = "cassandra_version") String cassandraVersion, @JsonProperty(value = "mgmt_version") String managementApiVersion, @JsonProperty(value = "features") List<Feature> availableFeatures) {
        this.cassandraVersion = cassandraVersion;
        this.mgmtVersion = managementApiVersion;
        this.features = availableFeatures;
    }

    public String getCassandraVersion() {
        return cassandraVersion;
    }

    public String getMgmtVersion() {
        return mgmtVersion;
    }

    public List<Feature> getFeatures() {
        return features;
    }
}
