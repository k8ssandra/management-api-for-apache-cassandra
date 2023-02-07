/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import io.k8ssandra.metrics.builder.relabel.RelabelSpec;
import io.k8ssandra.metrics.config.Configuration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;

/** https://cassandra.apache.org/doc/latest/cassandra/operating/metrics.html */
public class MetricsParsingTest {

  private RelabelSpec relabelTableName =
      new RelabelSpec(
          Lists.newArrayList("__origname__"),
          "",
          "org\\.apache\\.cassandra\\.metrics\\.Table\\.(\\w+)\\.(\\w+)\\.(\\w+)",
          "",
          "__name__",
          "org_apache_cassandra_metrics_table_$1");
  private RelabelSpec relabelTableLabel =
      new RelabelSpec(
          Lists.newArrayList("__origname__"),
          "",
          "org\\.apache\\.cassandra\\.metrics\\.Table\\.(\\w+)\\.(\\w+)\\.(\\w+)",
          "",
          "table",
          "$3");
  private RelabelSpec relabelTableKeyspaceLabel =
      new RelabelSpec(
          Lists.newArrayList("__origname__"),
          "",
          "org\\.apache\\.cassandra\\.metrics\\.Table\\.(\\w+)\\.(\\w+)\\.(\\w+)",
          "",
          "keyspace",
          "$2");

  private RelabelSpec relabelKeyspaceName =
      new RelabelSpec(
          Lists.newArrayList("__origname__"),
          "",
          "org\\.apache\\.cassandra\\.metrics\\.keyspace\\.(\\w+)\\.(\\w+)",
          "",
          "__name__",
          "org_apache_cassandra_metrics_keyspace_$1");
  private RelabelSpec relabelKeyspaceLabel =
      new RelabelSpec(
          Lists.newArrayList("__origname__"),
          "",
          "org\\.apache\\.cassandra\\.metrics\\.keyspace\\.(\\w+)\\.(\\w+)",
          "",
          "keyspace",
          "$2");

  @Test
  public void parseTableMetricName() {
    String dropwizardName =
        "org.apache.cassandra.metrics.Table.RepairedDataTrackingOverreadRows.system_schema.aggregates";

    Configuration config = new Configuration();
    config.setRelabels(
        Lists.newArrayList(relabelTableName, relabelTableLabel, relabelTableKeyspaceLabel));
    CassandraMetricNameParser parser =
        new CassandraMetricNameParser(Arrays.asList(""), Arrays.asList(""), config);
    CassandraMetricDefinition metricDefinition =
        parser.parseDropwizardMetric(dropwizardName, "", new ArrayList<>(), new ArrayList<>());

    assertEquals(-1, metricDefinition.getMetricName().indexOf("system_schema"));
    assertEquals(-1, metricDefinition.getMetricName().indexOf("aggregates"));

    Map<String, String> labels =
        toLabelMap(metricDefinition.getLabelNames(), metricDefinition.getLabelValues());

    assertTrue(labels.containsKey("keyspace"));
    assertEquals("system_schema", labels.get("keyspace"));

    assertTrue(labels.containsKey("table"));
    assertEquals("aggregates", labels.get("table"));
  }

  @Test
  public void parseKeyspaceName() {
    String dropwizardName =
        "org.apache.cassandra.metrics.keyspace.RepairJobsCompleted.system_schema";
    Configuration config = new Configuration();
    config.setRelabels(Lists.newArrayList(relabelKeyspaceName, relabelKeyspaceLabel));
    CassandraMetricNameParser parser =
        new CassandraMetricNameParser(Arrays.asList(""), Arrays.asList(""), config);
    CassandraMetricDefinition metricDefinition =
        parser.parseDropwizardMetric(dropwizardName, "", new ArrayList<>(), new ArrayList<>());
    assertEquals(-1, metricDefinition.getMetricName().indexOf("system_schema"));

    Map<String, String> labels =
        toLabelMap(metricDefinition.getLabelNames(), metricDefinition.getLabelValues());

    assertTrue(labels.containsKey("keyspace"));
    assertEquals("system_schema", labels.get("keyspace"));
  }

  private Map<String, String> toLabelMap(List<String> labelNames, List<String> labelValues) {
    Iterator<String> keyIter = labelNames.iterator();
    Iterator<String> valIter = labelValues.iterator();
    return IntStream.range(0, labelNames.size())
        .boxed()
        .collect(Collectors.toMap(_i -> keyIter.next(), _i -> valIter.next()));
  }

  @Test
  public void parseWeirdNames() {
    String dropwizardName = "com.datastax._weird_.constellation.schema$all*..__";
    CassandraMetricNameParser parser =
        new CassandraMetricNameParser(Arrays.asList(""), Arrays.asList(""), new Configuration());
    CassandraMetricDefinition metricDefinition =
        parser.parseDropwizardMetric(dropwizardName, "", new ArrayList<>(), new ArrayList<>());

    assertEquals("com_datastax_weird_constellation_schema_all_", metricDefinition.getMetricName());
  }
}
