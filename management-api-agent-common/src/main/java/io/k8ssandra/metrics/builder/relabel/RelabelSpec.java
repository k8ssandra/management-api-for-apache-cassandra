package io.k8ssandra.metrics.builder.relabel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Supports a subset of ServiceMonitorSpec's relabeling rules.
 *
 * - sourceLabels: [subsystem, server]
 *   separator: "@"
 *   regex: "kata@webserver"
 *   action: "drop"
 *
 * Like Prometheus, the metric name is available as source_label "__name__" and
 * the original Cassandra metric name is available as "__origname__"
 */
public class RelabelSpec {
    public enum Action {
        drop, keep, replace
    };

    public static final String METRIC_NAME_LABELNAME = "__name__";

    public static final String CASSANDRA_METRIC_NAME_LABELNAME = "__origname__";

    public static final String DEFAULT_SEPARATOR = "@";

    @JsonProperty("sourceLabels")
    private List<String> sourceLabels;

    @JsonProperty("targetLabel")
    private String targetLabel;

    @JsonProperty("replacement")
    private String replacement;

    @JsonProperty(value = "separator", defaultValue = DEFAULT_SEPARATOR)
    private String separator;

    @JsonProperty("regex")
    private Pattern regexp;

    @JsonProperty("action")
    private Action action = Action.replace;

    public RelabelSpec() {

    }

    @VisibleForTesting
    public RelabelSpec(List<String> sourceLabels, String separator, String regex, String action, String targetLabel, String replacement) {
        this.sourceLabels = sourceLabels;
        this.separator = separator;
        this.regexp = Pattern.compile(regex);
        if(!Strings.isNullOrEmpty(action)) {
            this.action = Action.valueOf(action);
        }
        this.targetLabel = targetLabel;
        this.replacement = replacement;
    }

    public List<String> getSourceLabels() {
        return sourceLabels;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public String getSeparator() {
        return separator;
    }

    public Pattern getRegexp() {
        return regexp;
    }

    public Action getAction() {
        return action;
    }

    public String getReplacement() {
        return replacement;
    }
}
