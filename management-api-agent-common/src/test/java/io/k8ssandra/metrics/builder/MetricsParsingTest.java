package io.k8ssandra.metrics.builder;

import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetricsParsingTest {

    @Test
    public void parseTableMetricName() {
        String dropwizardName = "org.apache.cassandra.metrics.Table.RepairedDataTrackingOverreadRows.system_schema.aggregates";

        CassandraMetricNameParser parser = new CassandraMetricNameParser(Arrays.asList(""), Arrays.asList(""));
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

}
