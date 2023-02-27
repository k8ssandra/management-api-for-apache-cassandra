/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.builder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import io.k8ssandra.metrics.builder.relabel.RelabelSpec;
import io.k8ssandra.metrics.config.Configuration;
import io.prometheus.client.Collector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CassandraMetricNameParser {
  public static final String KEYSPACE_METRIC_PREFIX = "org.apache.cassandra.metrics.keyspace.";
  public static final String TABLE_METRIC_PREFIX = "org.apache.cassandra.metrics.Table.";

  private final List<String> defaultLabelNames;
  private final List<String> defaultLabelValues;

  public static final String KEYSPACE_LABEL_NAME = "keyspace";
  public static final String TABLE_LABEL_NAME = "table";

  private final List<RelabelSpec> replacements = new ArrayList<>();

  public CassandraMetricNameParser(
      List<String> defaultLabelNames, List<String> defaultLabelValues, Configuration config) {
    this.defaultLabelNames = new ArrayList<>(defaultLabelNames.size() + 2);
    this.defaultLabelValues = new ArrayList<>(defaultLabelValues.size() + 2);
    this.defaultLabelNames.addAll(defaultLabelNames);
    this.defaultLabelValues.addAll(defaultLabelValues);

    if (config.getLabels() != null
        && config.getLabels().getEnvVariables() != null
        && config.getLabels().getEnvVariables().size() > 0) {
      this.parseEnvVariablesAsLabels(config.getLabels().getEnvVariables());
    }

    if (config.getRelabels() != null) {
      replacements.addAll(config.getRelabels());
    }
  }

  private void parseEnvVariablesAsLabels(Map<String, String> envSettings) {
    for (Map.Entry<String, String> entry : envSettings.entrySet()) {
      String envValue = System.getenv(entry.getValue());
      if (envValue != null) {
        defaultLabelNames.add(entry.getKey());
        defaultLabelValues.add(envValue);
      }
    }
  }

  /**
   * Parse DropwizardMetricNames to a shorter version with labels added for Prometheus use
   *
   * @param dropwizardName Original metric name in the Dropwizard MetricRegistry
   * @param suffix DropwizardExporter's _count, _total and other additional calculated metrics (not
   *     part of CassandraMetricsRegistry)
   * @return
   */
  public CassandraMetricDefinition parseDropwizardMetric(
      String dropwizardName,
      String suffix,
      List<String> additionalLabelNames,
      List<String> additionalLabelValues) {
    List<String> labelNames = Lists.newArrayList(defaultLabelNames);
    List<String> labelValues = Lists.newArrayList(defaultLabelValues);

    String metricName =
        removeDoubleUnderscore(Collector.sanitizeMetricName(this.clean(dropwizardName)));
    ;
    CassandraMetricDefinition metricDef =
        new CassandraMetricDefinition(metricName, labelNames, labelValues);

    // Process replace rules here
    replace(dropwizardName, metricDef);

    labelNames.addAll(additionalLabelNames);
    labelValues.addAll(additionalLabelValues);

    // Reclean with suffix added
    metricDef.setMetricName(
        removeDoubleUnderscore(
            Collector.sanitizeMetricName(this.clean(metricDef.getMetricName()) + suffix)));

    return metricDef;
  }

  @VisibleForTesting
  public void replace(String dropwizardName, CassandraMetricDefinition metricDefinition) {
    boolean keep = true;

    // Return value is the new metricName, labelNames / labelValues are modified
    for (RelabelSpec relabel : replacements) {
      // Shared code with the other filter infra.. perhaps we should just use a single impl?
      HashMap<String, String> labels =
          getLabels(
              dropwizardName,
              metricDefinition.getMetricName(),
              metricDefinition.getLabelNames(),
              metricDefinition.getLabelValues());

      String separator = relabel.getSeparator();
      if (separator == null) {
        separator = RelabelSpec.DEFAULT_SEPARATOR;
      }
      StringJoiner joiner = new StringJoiner(separator);
      for (String sourceLabel : relabel.getSourceLabels()) {
        String labelValue = labels.get(sourceLabel);
        if (labelValue == null) {
          labelValue = "";
        }
        joiner.add(labelValue);
      }

      String value = joiner.toString();
      Pattern inputPattern = relabel.getRegexp();
      Matcher inputMatcher = inputPattern.matcher(value);

      switch (relabel.getAction()) {
        case replace:
          if (!inputMatcher.matches()) {
            continue;
          }

          if (relabel.getTargetLabel() == null || relabel.getTargetLabel().length() < 1) {
            // This is invalid definition, just skip it
            continue;
          }

          String output = inputMatcher.replaceAll(relabel.getReplacement());

          if (relabel.getTargetLabel().equals(RelabelSpec.METRIC_NAME_LABELNAME)) {
            metricDefinition.setMetricName(output);
          } else {
            // Not the most effective way of doing this (map would be better - but perf is not an
            // issue since this isn't done in the hotpath)
            boolean replaced = false;
            for (int i = 0; i < metricDefinition.getLabelNames().size(); i++) {
              if (metricDefinition.getLabelNames().get(i).equals(relabel.getTargetLabel())) {
                // Replace existing label value
                metricDefinition.getLabelValues().set(i, output);
                replaced = true;
              }
            }
            if (!replaced) {
              metricDefinition.getLabelNames().add(relabel.getTargetLabel());
              metricDefinition.getLabelValues().add(output);
            }
          }
          break;
        case drop:
          boolean noMatch = !relabel.getRegexp().matcher(value).matches();
          keep &= noMatch;
          break;
        case keep:
          boolean match = relabel.getRegexp().matcher(value).matches();
          keep &= match;
          break;
        default:
      }
    }

    // Set keep here and discard later in the process
    metricDefinition.setKeep(keep);
  }

  private HashMap<String, String> getLabels(
      String dropwizardName, String metricName, List<String> labelNames, List<String> labelValues) {
    HashMap<String, String> labels = new HashMap<>();
    for (int i = 0; i < labelValues.size(); i++) {
      labels.put(labelNames.get(i), labelValues.get(i));
    }

    labels.put(RelabelSpec.METRIC_NAME_LABELNAME, metricName);
    labels.put(RelabelSpec.CASSANDRA_METRIC_NAME_LABELNAME, dropwizardName);

    return labels;
  }

  private String removeDoubleUnderscore(String name) {
    name = name.replaceAll("_+", "_");
    return name;
  }

  // This is the method used in the MCAC
  private String clean(String name) {
    // Special case for coda hale metrics
    if (name.startsWith("jvm")) {
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

    // Convert camelCase to snake_case
    name = String.join("_", name.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])"));
    name = name.replaceAll("\\._", "\\.");
    name = name.replaceAll("_+", "_");

    return name.toLowerCase();
  }
}
