package io.k8ssandra.metrics.builder;

import io.prometheus.client.Collector;

import java.util.ArrayList;
import java.util.List;

public class CassandraMetricNameParser {
    public final static String KEYSPACE_METRIC_PREFIX = "org.apache.cassandra.metrics.keyspace.";
    public final static String TABLE_METRIC_PREFIX = "org.apache.cassandra.metrics.Table.";

    private final List<String> defaultLabelNames;
    private final List<String> defaultLabelValues;

    public final static String KEYSPACE_LABEL_NAME = "keyspace";
    public final static String TABLE_LABEL_NAME = "table";

    public CassandraMetricNameParser(List<String> defaultLabelNames, List<String> defaultLabelValues) {
        this.defaultLabelNames = defaultLabelNames;
        this.defaultLabelValues = defaultLabelValues;
    }

    /**
     * Parse DropwizardMetricNames to a shorter version with labels added for Prometheus use
     *
     * @param dropwizardName Original metric name in the Dropwizard MetricRegistry
     * @param suffix DropwizardExporter's _count, _total and other additional calculated metrics (not part of CassandraMetricsRegistry)
     * @return
     */
    public CassandraMetricDefinition parseDropwizardMetric(String dropwizardName, String suffix, List<String> additionalLabelNames, List<String> additionalLabelValues) {
        String metricName = dropwizardName;

        List<String> labelNames = new ArrayList<>();
        List<String> labelValues = new ArrayList<>();

        labelNames.addAll(defaultLabelNames);
        labelValues.addAll(defaultLabelValues);

        if(dropwizardName.startsWith(KEYSPACE_METRIC_PREFIX)) {
            int keyspaceIndex = dropwizardName.lastIndexOf(".");

            // Remove keyspace from the metric name
            metricName = dropwizardName.substring(0, keyspaceIndex);

            // Add keyspace as the label
            String keyspace = dropwizardName.substring(keyspaceIndex+1);
            labelNames.add(KEYSPACE_LABEL_NAME);
            labelValues.add(keyspace);
        } else if(dropwizardName.startsWith(TABLE_METRIC_PREFIX)) {
            int tableIndex = dropwizardName.lastIndexOf(".");
            // len(org.apache.cassandra.metrics.Table.) == 35
            int keyspaceIndex = dropwizardName.substring(0, tableIndex).lastIndexOf(".");

            metricName = dropwizardName.substring(0, keyspaceIndex);

            String keyspace = dropwizardName.substring(keyspaceIndex+1, tableIndex);
            String table = dropwizardName.substring(tableIndex+1);

            // Add keyspace and table as labels
            labelNames.add(KEYSPACE_LABEL_NAME);
            labelValues.add(keyspace);
            labelNames.add(TABLE_LABEL_NAME);
            labelValues.add(table);
        }

        metricName = this.clean(Collector.sanitizeMetricName(metricName + suffix));

        labelNames.addAll(additionalLabelNames);
        labelValues.addAll(additionalLabelValues);

        return new CassandraMetricDefinition(metricName, labelNames, labelValues);
    }

    // This is the method used in the MCAC
    private String clean(String name)
    {
        // Special case for coda hale metrics
        if (name.startsWith("jvm"))
        {
            name = name.replaceAll("\\-", "_");
            return name.toLowerCase();
        }

        name = name.replaceAll("\\s*,\\s*", ",");
        name = name.replaceAll("\\s+", "_");
        name = name.replaceAll("\\\\", "_");
        name = name.replaceAll("/", "_");

        name = name.replaceAll("[^a-zA-Z0-9\\.\\_]+", ".");
        name = name.replaceAll("\\.+", ".");
        name = name.replaceAll("_+", "_");

        //Convert camelCase to snake_case
        name = String.join("_", name.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])"));
        name = name.replaceAll("\\._", "\\.");
        name = name.replaceAll("_+", "_");

        return name.toLowerCase();
    }
}
