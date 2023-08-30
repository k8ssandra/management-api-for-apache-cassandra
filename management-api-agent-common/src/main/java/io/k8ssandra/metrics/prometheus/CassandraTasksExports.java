/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.prometheus;

import com.codahale.metrics.MetricRegistry;
import com.datastax.mgmtapi.ShimLoader;
import com.google.common.collect.Lists;
import io.k8ssandra.metrics.builder.CassandraMetricDefinition;
import io.k8ssandra.metrics.builder.CassandraMetricNameParser;
import io.k8ssandra.metrics.config.Configuration;
import io.prometheus.client.Collector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.cassandra.db.compaction.OperationType;
import org.slf4j.LoggerFactory;

/**
 * Collect non-metrics information from Cassandra and turn them to metrics. This is considerably
 * slower to collect than the metrics we currently ship.
 */
public class CassandraTasksExports extends Collector implements Collector.Describable {
  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(CassandraTasksExports.class);

  private final MetricRegistry registry;

  private final CassandraMetricNameParser parser;

  public CassandraTasksExports(MetricRegistry registry, Configuration config) {
    this.registry = registry;
    parser = CassandraMetricNameParser.getDefaultParser(config);
  }

  @Override
  public List<MetricFamilySamples> collect() {

    ArrayList<MetricFamilySamples> familySamples = Lists.newArrayList();

    // Collect Compaction Task metrics
    familySamples.addAll(getCompactionStats());

    // Collect active streaming sessions

    // Collect other sstableOperations (if not part of Compactions metrics already)

    // Collect JobExecutor tasks

    // Collect MBean ones not exposed currently in CassandraMetrics / 3.11

    return familySamples;
  }

  @Override
  public List<MetricFamilySamples> describe() {
    return new ArrayList<>();
  }

  List<MetricFamilySamples> getCompactionStats() {

    // Cassandra's internal CompactionMetrics are close to what we want, but not exactly.
    // And we can't access CompactionManager.getMetrics() to get them in 3.11
    List<Map<String, String>> compactions =
        ShimLoader.instance.get().getCompactionManager().getCompactions().stream()
            .filter(
                c -> {
                  String taskType = c.get("taskType");
                  OperationType operationType = OperationType.valueOf(taskType);
                  return operationType != OperationType.COUNTER_CACHE_SAVE
                      && operationType != OperationType.KEY_CACHE_SAVE
                      && operationType != OperationType.ROW_CACHE_SAVE;
                })
            .collect(Collectors.toList());

    // Ignore taskTypes: COUNTER_CACHE_SAVE, KEY_CACHE_SAVE, ROW_CACHE_SAVE (from Cassandra 4.1)
    ArrayList<String> additionalLabels =
        Lists.newArrayList("keyspace", "table", "compaction_id", "unit", "type");

    // These should be escape handled
    CassandraMetricDefinition protoCompleted =
        parser.parseDropwizardMetric(
            "org_apache_cassandra_metrics_extended_compaction_stats_completed",
            "",
            additionalLabels,
            null);

    CassandraMetricDefinition protoTotal =
        parser.parseDropwizardMetric(
            "org_apache_cassandra_metrics_extended_compaction_stats_total",
            "",
            additionalLabels,
            null);

    List<MetricFamilySamples.Sample> completedSamples = new ArrayList<>(compactions.size() * 2);
    List<MetricFamilySamples.Sample> totalSamples = new ArrayList<>(compactions.size() * 2);
    for (Map<String, String> c : compactions) {
      ArrayList<String> labelValues =
          Lists.newArrayList(
              c.get("keyspace"),
              c.get("columnfamily"),
              c.get("compactionId"),
              c.get("unit"),
              c.get("taskType"));

      Collector.MetricFamilySamples.Sample completeSample =
          new Collector.MetricFamilySamples.Sample(
              protoCompleted.getMetricName(),
              protoCompleted.getLabelNames(),
              labelValues,
              Double.parseDouble(c.get("completed")));

      Collector.MetricFamilySamples.Sample totalSample =
          new Collector.MetricFamilySamples.Sample(
              protoCompleted.getMetricName(),
              protoCompleted.getLabelNames(),
              labelValues,
              Double.parseDouble(c.get("total")));

      completedSamples.add(completeSample);
      totalSamples.add(totalSample);
    }

    MetricFamilySamples completeFamily =
        new MetricFamilySamples(protoCompleted.getMetricName(), Type.GAUGE, "", completedSamples);

    MetricFamilySamples totalFamily =
        new MetricFamilySamples(protoTotal.getMetricName(), Type.GAUGE, "", totalSamples);

    return Lists.newArrayList(completeFamily, totalFamily);
  }
}
