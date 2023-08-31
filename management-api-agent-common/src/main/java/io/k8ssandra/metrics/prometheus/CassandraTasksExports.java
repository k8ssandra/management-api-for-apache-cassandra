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

  private static final String METRICS_PREFIX = "org_apache_cassandra_metrics_extended_";
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
    familySamples.addAll(getStreamInfoStats());

    // Collect other sstableOperations (if not part of Compactions metrics already)

    // Collect JobExecutor tasks

    // Collect MBean ones not exposed currently in CassandraMetrics / 3.11

    return familySamples;
  }

  @Override
  public List<MetricFamilySamples> describe() {
    return new ArrayList<>();
  }

  List<MetricFamilySamples> getStreamInfoStats() {
    ArrayList<String> additionalLabels =
        Lists.newArrayList("plan_id", "operation", "peer", "connection");

    // These should be EA targets, 8 metrics to create
    CassandraMetricDefinition filesToReceive =
        parser.parseDropwizardMetric(
            METRICS_PREFIX + "streaming_total_files_to_receive", "", additionalLabels, null);

    CassandraMetricDefinition filesReceived =
        parser.parseDropwizardMetric(
            METRICS_PREFIX + "streaming_total_files_received", "", additionalLabels, null);

    CassandraMetricDefinition sizeToReceive =
        parser.parseDropwizardMetric(
            METRICS_PREFIX + "streaming_total_size_to_receive", "", additionalLabels, null);

    CassandraMetricDefinition sizeReceived =
        parser.parseDropwizardMetric(
            METRICS_PREFIX + "streaming_total_size_received", "", additionalLabels, null);

    CassandraMetricDefinition filesToSend =
        parser.parseDropwizardMetric(
            METRICS_PREFIX + "streaming_total_files_to_send", "", additionalLabels, null);

    CassandraMetricDefinition filesSent =
        parser.parseDropwizardMetric(
            METRICS_PREFIX + "streaming_total_files_sent", "", additionalLabels, null);

    CassandraMetricDefinition sizeToSend =
        parser.parseDropwizardMetric(
            METRICS_PREFIX + "streaming_total_size_to_send", "", additionalLabels, null);

    CassandraMetricDefinition sizeSent =
        parser.parseDropwizardMetric(
            METRICS_PREFIX + "streaming_total_size_sent", "", additionalLabels, null);

    // This is a lot simpler code without all the casting back and forth if description was in the
    // same place for
    // 3.11 and 4.x. Simplify this once 3.11 support is dropped to use
    // StreamManager.instance.getCurrentStreams() ..

    List<Map<String, List<Map<String, String>>>> streamInfos =
        ShimLoader.instance.get().getStreamInfo();

    List<MetricFamilySamples.Sample> totalFilesToReceiveSamples = new ArrayList<>();
    List<MetricFamilySamples.Sample> totalFilesReceivedSamples = new ArrayList<>();
    List<MetricFamilySamples.Sample> totalSizeToReceiveSamples = new ArrayList<>();
    List<MetricFamilySamples.Sample> totalSizeReceivedSamples = new ArrayList<>();
    List<MetricFamilySamples.Sample> totalFilesToSendSamples = new ArrayList<>();
    List<MetricFamilySamples.Sample> totalFilesSentSamples = new ArrayList<>();
    List<MetricFamilySamples.Sample> totalSizeToSendSamples = new ArrayList<>();
    List<MetricFamilySamples.Sample> totalSizeSentSamples = new ArrayList<>();

    for (Map<String, List<Map<String, String>>> streamInfo : streamInfos) {
      for (Map.Entry<String, List<Map<String, String>>> sessionResults : streamInfo.entrySet()) {
        String planId = sessionResults.getKey();
        for (Map<String, String> session : sessionResults.getValue()) {
          ArrayList<String> labelValues =
              Lists.newArrayList(
                  planId,
                  session.get("STREAM_OPERATION"),
                  session.get("PEER"),
                  session.get("USING_CONNECTION"));

          long totalFilesToReceive = Long.parseLong(session.get("TOTAL_FILES_TO_RECEIVE"));
          long totalFilesReceived = Long.parseLong(session.get("TOTAL_FILES_RECEIVED"));
          long totalSizeToReceive = Long.parseLong(session.get("TOTAL_SIZE_TO_RECEIVE"));
          long totalSizeReceived = Long.parseLong(session.get("TOTAL_SIZE_RECEIVED"));
          long totalFilesToSend = Long.parseLong(session.get("TOTAL_FILES_TO_SEND"));
          long totalFilesSent = Long.parseLong(session.get("TOTAL_FILES_SENT"));
          long totalSizeToSend = Long.parseLong(session.get("TOTAL_SIZE_TO_SEND"));
          long totalSizeSent = Long.parseLong(session.get("TOTAL_SIZE_SENT"));

          // Receive samples
          Collector.MetricFamilySamples.Sample totalFilesToReceiveSample =
              new Collector.MetricFamilySamples.Sample(
                  filesToReceive.getMetricName(),
                  filesToReceive.getLabelNames(),
                  labelValues,
                  totalFilesToReceive);

          totalFilesToReceiveSamples.add(totalFilesToReceiveSample);

          Collector.MetricFamilySamples.Sample totalFilesReceivedSample =
              new Collector.MetricFamilySamples.Sample(
                  filesReceived.getMetricName(),
                  filesReceived.getLabelNames(),
                  labelValues,
                  totalFilesReceived);

          totalFilesReceivedSamples.add(totalFilesReceivedSample);

          Collector.MetricFamilySamples.Sample totalSizeToReceiveSample =
              new Collector.MetricFamilySamples.Sample(
                  sizeToReceive.getMetricName(),
                  sizeToReceive.getLabelNames(),
                  labelValues,
                  totalSizeToReceive);

          totalSizeToReceiveSamples.add(totalSizeToReceiveSample);

          Collector.MetricFamilySamples.Sample totalSizeReceivedSample =
              new Collector.MetricFamilySamples.Sample(
                  sizeReceived.getMetricName(),
                  sizeReceived.getLabelNames(),
                  labelValues,
                  totalSizeReceived);

          totalSizeReceivedSamples.add(totalSizeReceivedSample);

          // Send samples
          Collector.MetricFamilySamples.Sample totalFilesToSendSample =
              new Collector.MetricFamilySamples.Sample(
                  filesToSend.getMetricName(),
                  filesToSend.getLabelNames(),
                  labelValues,
                  totalFilesToSend);

          totalFilesToSendSamples.add(totalFilesToSendSample);

          Collector.MetricFamilySamples.Sample totalFilesSentSample =
              new Collector.MetricFamilySamples.Sample(
                  filesSent.getMetricName(),
                  filesSent.getLabelNames(),
                  labelValues,
                  totalFilesSent);

          totalFilesSentSamples.add(totalFilesSentSample);

          Collector.MetricFamilySamples.Sample totalSizeToSendSample =
              new Collector.MetricFamilySamples.Sample(
                  sizeToSend.getMetricName(),
                  sizeToSend.getLabelNames(),
                  labelValues,
                  totalSizeToSend);

          totalSizeToSendSamples.add(totalSizeToSendSample);

          Collector.MetricFamilySamples.Sample totalSizeSentSample =
              new Collector.MetricFamilySamples.Sample(
                  sizeSent.getMetricName(), sizeSent.getLabelNames(), labelValues, totalSizeSent);

          totalSizeSentSamples.add(totalSizeSentSample);
        }
      }
    }

    // Receive
    MetricFamilySamples filesToReceiveFamily =
        new MetricFamilySamples(
            filesToReceive.getMetricName(), Type.GAUGE, "", totalFilesToReceiveSamples);

    MetricFamilySamples filesReceivedFamily =
        new MetricFamilySamples(
            filesReceived.getMetricName(), Type.GAUGE, "", totalFilesReceivedSamples);

    MetricFamilySamples sizeToReceiveFamily =
        new MetricFamilySamples(
            sizeToReceive.getMetricName(), Type.GAUGE, "", totalSizeToReceiveSamples);

    MetricFamilySamples sizeReceivedFamily =
        new MetricFamilySamples(
            sizeReceived.getMetricName(), Type.GAUGE, "", totalSizeReceivedSamples);

    // Send
    MetricFamilySamples filesToSendFamily =
        new MetricFamilySamples(
            filesToSend.getMetricName(), Type.GAUGE, "", totalFilesToSendSamples);

    MetricFamilySamples filesSentFamily =
        new MetricFamilySamples(filesSent.getMetricName(), Type.GAUGE, "", totalFilesSentSamples);

    MetricFamilySamples sizeToSendFamily =
        new MetricFamilySamples(sizeToSend.getMetricName(), Type.GAUGE, "", totalSizeToSendSamples);

    MetricFamilySamples sizeSentFamily =
        new MetricFamilySamples(sizeSent.getMetricName(), Type.GAUGE, "", totalSizeSentSamples);

    return Lists.newArrayList(
        filesToReceiveFamily,
        filesReceivedFamily,
        sizeToReceiveFamily,
        sizeReceivedFamily,
        filesToSendFamily,
        filesSentFamily,
        sizeToSendFamily,
        sizeSentFamily);
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

    // These should be EA targets..
    CassandraMetricDefinition protoCompleted =
        parser.parseDropwizardMetric(
            METRICS_PREFIX + "compaction_stats_completed", "", additionalLabels, null);

    CassandraMetricDefinition protoTotal =
        parser.parseDropwizardMetric(
            METRICS_PREFIX + "compaction_stats_total", "", additionalLabels, null);

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
