package io.k8ssandra.metrics.builder;

import io.k8ssandra.metrics.builder.parsing.Replacements;
import io.k8ssandra.metrics.config.Configuration;
import org.junit.Test;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetricsParsingTest {

    @Test
    public void parseTableMetricName() {
        String dropwizardName = "org.apache.cassandra.metrics.Table.RepairedDataTrackingOverreadRows.system_schema.aggregates";

        CassandraMetricNameParser parser = new CassandraMetricNameParser(Arrays.asList(""), Arrays.asList(""), new Configuration());
        CassandraMetricDefinition metricDefinition = parser.parseDropwizardMetric(dropwizardName, "", new ArrayList<>(), new ArrayList<>());

        assertEquals(-1, metricDefinition.getMetricName().indexOf("system_schema"));
        assertEquals(-1, metricDefinition.getMetricName().indexOf("aggregates"));

        Map<String, String> labels = toLabelMap(metricDefinition.getLabelNames(), metricDefinition.getLabelValues());

        assertTrue(labels.containsKey("keyspace"));
        assertEquals("system_schema", labels.get("keyspace"));

        assertTrue(labels.containsKey("table"));
        assertEquals("aggregates", labels.get("table"));
    }

    private Map<String, String> toLabelMap(List<String> labelNames, List<String> labelValues) {
        Iterator<String> keyIter = labelNames.iterator();
        Iterator<String> valIter = labelValues.iterator();
        return IntStream.range(0, labelNames.size()).boxed()
                .collect(Collectors.toMap(_i -> keyIter.next(), _i -> valIter.next()));
    }

    @Test
    public void parseWeirdNames() {
        String dropwizardName = "com.datastax._weird_.constellation.schema$all*..__";
        CassandraMetricNameParser parser = new CassandraMetricNameParser(Arrays.asList(""), Arrays.asList(""), new Configuration());
        CassandraMetricDefinition metricDefinition = parser.parseDropwizardMetric(dropwizardName, "", new ArrayList<>(), new ArrayList<>());

        assertEquals("com_datastax_weird_constellation_schema_all_", metricDefinition.getMetricName());
    }

    @Test
    public void replacementTable() {
        String dropwizardName = "org.apache.cassandra.metrics.Table.RepairedDataTrackingOverreadRows.system_schema.aggregates";
        Replacements replacements = new Replacements();
        String regexp = "org\\.apache\\.cassandra\\.metrics\\.(\\w+)\\.(.+)$";
        String replacement = "$1 $2";
        replacements.replaceDropwizardMetric(dropwizardName, regexp, replacement);

    }
}
