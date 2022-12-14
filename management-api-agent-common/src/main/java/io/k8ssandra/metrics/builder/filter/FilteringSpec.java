package io.k8ssandra.metrics.builder.filter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Supports a subset of ServiceMonitorSpec's relabeling rules.
 *
 *             - source_labels: [subsystem, server]
 *               separator: "@"
 *               regex: "kata@webserver"
 *               action: "drop"
 *
 * Like Prometheus, the metric name is available as source_label "__name__" and the original Cassandra metric
 * name is available as "__origname__"
 */
public class FilteringSpec {
    public enum Action { drop, keep };

    public static final String METRIC_NAME_LABELNAME = "__name__";

    public static final String CASSANDRA_METRIC_NAME_LABELNAME = "__origname__";

    public static final String DEFAULT_SEPARATOR = "@";

    @JsonProperty("source_labels")
    private List<String> sourceLabels;

    @JsonProperty(value = "separator", defaultValue = DEFAULT_SEPARATOR)
    private String separator;

    @JsonProperty("regex")
    private Pattern regexp;

    @JsonProperty("action")
    private Action action;

    public FilteringSpec() {

    }

    public FilteringSpec(List<String> sourceLabels, String separator, String regex, String action) {
        this.sourceLabels = sourceLabels;
        this.separator = separator;
        this.regexp = Pattern.compile(regex);
        this.action = Action.valueOf(action);
    }

    public boolean filter(Map<String, String> labels) {
        StringJoiner joiner = new StringJoiner(DEFAULT_SEPARATOR);
        for (String sourceLabel : sourceLabels) {
            String labelValue = labels.get(sourceLabel);
            if(labelValue == null) {
                labelValue = "";
            }
            joiner.add(labelValue);
        }

        String value = joiner.toString();

        boolean match = regexp.matcher(value).matches();

        switch(action) {
            case drop:
                return !match;
            default:
                return match;
        }
    }

}
