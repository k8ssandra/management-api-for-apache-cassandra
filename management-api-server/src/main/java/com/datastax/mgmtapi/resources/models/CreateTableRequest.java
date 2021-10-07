package com.datastax.mgmtapi.resources.models;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.internal.core.metadata.schema.parsing.DataTypeCqlNameParser;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateTableRequest implements Serializable
{

    @JsonProperty(value = "keyspace_name", required = true)
    public final String keyspaceName;

    @JsonProperty(value = "table_name", required = true)
    public final String tableName;

    @JsonProperty(value = "columns", required = true)
    public final List<Column> columns;

    @JsonProperty(value = "options")
    public final Map<String, Object> options;

    @JsonCreator
    public CreateTableRequest(@JsonProperty(value = "keyspace_name", required = true) String keyspaceName,
                              @JsonProperty(value = "table_name", required = true) String tableName,
                              @JsonProperty(value = "columns", required = true) List<Column> columns,
                              @JsonProperty("options") Map<String, Object> options)
    {
        this.keyspaceName = keyspaceName;
        this.tableName = tableName;
        this.columns = columns;
        this.options = options;
    }

    @JsonIgnore
    public Map<String, String> columnNamesAndTypes()
    {
        return columns.stream().collect(Collectors.toMap(c -> c.name, c -> c.type));
    }

    @JsonIgnore
    public List<String> partitionKeyColumnNames()
    {
        return columns.stream()
                      .filter(c -> c.kind == ColumnKind.PARTITION_KEY)
                      .sorted(Comparator.comparingInt(c -> c.position))
                      .map(c -> c.name)
                      .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<String> clusteringColumnNames()
    {
        return columns.stream()
                      .filter(c -> c.kind == ColumnKind.CLUSTERING_COLUMN)
                      .sorted(Comparator.comparingInt(c -> c.position))
                      .map(c -> c.name)
                      .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<String> clusteringOrders()
    {
        return columns.stream()
                      .filter(c -> c.kind == ColumnKind.CLUSTERING_COLUMN)
                      .sorted(Comparator.comparingInt(c -> c.position))
                      .map(c -> c.order.name())
                      .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<String> staticColumnNames()
    {
        return columns.stream()
                      .filter(c -> c.kind == ColumnKind.STATIC)
                      .map(c -> c.name)
                      .collect(Collectors.toList());
    }

    public void validate()
    {
        if (StringUtils.isBlank(keyspaceName))
        {
            throw new RuntimeException("'keyspace_name' must not be empty");
        }
        if (StringUtils.isBlank(tableName))
        {
            throw new RuntimeException("'table_name' must not be empty");
        }
        if (null == columns || columns.isEmpty())
        {
            throw new RuntimeException("'columns' must not be empty");
        }
        Set<String> names = new HashSet<>();
        CqlIdentifier keyspace = CqlIdentifier.fromInternal(keyspaceName);
        for (int i = 0; i < columns.size(); i++)
        {
            Column column = columns.get(i);
            column.validate(i, keyspace);
            if (names.contains(column.name))
            {
                throw new RuntimeException(String.format("duplicated column name: '%s'", column.name));
            }
            names.add(column.name);
        }
        validatePartitionKey();
        validateClusteringColumns();
        if (options != null && !options.isEmpty())
        {
            validateOptions();
        }
    }

    private void validatePartitionKey()
    {
        Map<Integer, Integer> partitionKey = columns.stream()
            .filter(c -> c.kind == ColumnKind.PARTITION_KEY)
            .collect(Collectors.groupingBy(c -> c.position, TreeMap::new, Collectors.summingInt(c -> 1)));
        if (partitionKey.isEmpty())
        {
            throw new RuntimeException("invalid primary key: partition key is empty");
        }
        for (int i = 0; i < partitionKey.size(); i++)
        {
            Integer count = partitionKey.get(i);
            if (count == null)
            {
                throw new RuntimeException("invalid primary key: missing partition key at position " + i);
            }
            else if (count != 1)
            {
                throw new RuntimeException(String.format("invalid primary key: found %d partition key columns at position %d", count, i));
            }
        }
    }

    private void validateClusteringColumns()
    {
        Map<Integer, Integer> clusteringColumns = columns.stream()
             .filter(c -> c.kind == ColumnKind.CLUSTERING_COLUMN)
             .collect(Collectors.groupingBy(c -> c.position, TreeMap::new, Collectors.summingInt(c -> 1)));
        for (int i = 0; i < clusteringColumns.size(); i++)
        {
            Integer count = clusteringColumns.get(i);
            if (count == null)
            {
                throw new RuntimeException("invalid primary key: missing clustering column at position " + i);
            }
            else if (count != 1)
            {
                throw new RuntimeException(String.format("invalid primary key: found %d clustering columns at position %d", count, i));
            }
        }
    }

    private void validateOptions()
    {
        for (Map.Entry<String, Object> entry : options.entrySet())
        {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map)
            {
                Map<?, ?> map = (Map<?, ?>) value;
                for (Map.Entry<?, ?> childEntry : map.entrySet())
                {
                    if (!(childEntry.getKey() instanceof String) || !(childEntry.getValue() instanceof String))
                    {
                        throw new RuntimeException(String.format("invalid value for option '%s': expected String or Map<String,String>, got: %s", key, value));
                    }
                }
            }
            else if (!(value instanceof String))
            {
                throw new RuntimeException(String.format("invalid value for option '%s': expected String or Map<String,String>, got: %s", key, value));
            }
        }
    }

    public Map<String, String> simpleOptions()
    {
        if (options == null || options.isEmpty())
        {
            return Collections.emptyMap();
        }
        return options.entrySet().stream()
                      .filter(entry -> entry.getValue() instanceof String)
                      .collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Map<String, String>> complexOptions()
    {
        if (options == null || options.isEmpty())
        {
            return Collections.emptyMap();
        }
        return options.entrySet().stream()
                      .filter(entry -> entry.getValue() instanceof Map)
                      .collect(Collectors.toMap(Map.Entry::getKey, e -> (Map<String, String>) e.getValue()));
    }

    public static class Column
    {

        private static final DataTypeCqlNameParser DATA_TYPE_PARSER = new DataTypeCqlNameParser();

        @JsonProperty(value = "name", required = true)
        public final String name;

        @JsonProperty(value = "type", required = true)
        public final String type;

        @JsonProperty(value = "kind", defaultValue = "REGULAR")
        public final ColumnKind kind;

        @JsonProperty(value = "position")
        public final int position;

        @JsonProperty(value = "order")
        public final ClusteringOrder order;

        @JsonCreator
        public Column(@JsonProperty(value = "name", required = true) String name,
                      @JsonProperty(value = "type", required = true) String type,
                      @JsonProperty(value = "kind", defaultValue = "REGULAR") ColumnKind kind,
                      @JsonProperty(value = "position") int position,
                      @JsonProperty(value = "order") ClusteringOrder order)
        {
            this.name = name;
            this.type = type;
            this.kind = kind;
            this.position = position;
            this.order = order;
        }

        public void validate(int index, CqlIdentifier keyspace)
        {
            if (StringUtils.isBlank(name))
            {
                throw new RuntimeException(String.format("'columns[%d].name' must not be empty", index));
            }
            if (StringUtils.isBlank(type))
            {
                throw new RuntimeException(String.format("'columns[%d].type' must not be empty", index));
            }
            else
            {
                try
                {
                    DATA_TYPE_PARSER.parse(keyspace, type, null, null);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(String.format("'columns[%d].type' is invalid: %s", index, e.getMessage()));
                }
            }
            if (kind == null)
            {
                throw new RuntimeException(String.format("'columns[%d].kind' must not be null", index));
            }
            if (kind == ColumnKind.PARTITION_KEY)
            {
                if (position < 0)
                {
                    throw new RuntimeException(String.format("'columns[%d].position' must not be negative for partition key columns", index));
                }
            }
            if (kind == ColumnKind.CLUSTERING_COLUMN)
            {
                if (position < 0)
                {
                    throw new RuntimeException(String.format("'columns[%d].position' must not be negative for clustering columns", index));
                }
                if (order == null)
                {
                    throw new RuntimeException(String.format("'columns[%d].order' must not be empty for clustering columns", index));
                }
            }
        }
    }

    public enum ColumnKind
    {
        PARTITION_KEY, CLUSTERING_COLUMN, REGULAR, STATIC
    }
}
