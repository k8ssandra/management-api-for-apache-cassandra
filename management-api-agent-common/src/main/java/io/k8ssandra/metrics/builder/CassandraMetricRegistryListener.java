/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.builder;

import static io.k8ssandra.metrics.builder.CassandraMetricsTools.BUCKET_LABEL_NAME;
import static io.k8ssandra.metrics.builder.CassandraMetricsTools.INF_BUCKET;
import static io.k8ssandra.metrics.builder.CassandraMetricsTools.LATENCY_OFFSETS;
import static io.k8ssandra.metrics.builder.CassandraMetricsTools.LATENCY_OFFSETS_TEXT;
import static io.k8ssandra.metrics.builder.CassandraMetricsTools.PRECOMPUTED_QUANTILES;
import static io.k8ssandra.metrics.builder.CassandraMetricsTools.PRECOMPUTED_QUANTILES_TEXT;
import static io.k8ssandra.metrics.builder.CassandraMetricsTools.QUANTILE_LABEL_NAME;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import io.k8ssandra.metrics.config.Configuration;
import io.prometheus.client.Collector;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.cassandra.utils.CassandraVersion;
import org.apache.cassandra.utils.EstimatedHistogram;
import org.apache.cassandra.utils.FBUtilities;
import org.slf4j.LoggerFactory;

public class CassandraMetricRegistryListener implements MetricRegistryListener {

  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(CassandraMetricRegistryListener.class);
  /**
   * For DSE versions less than 6.8.33, there is a metric initialization bug that results in a NPE
   * when certain metrics are added (see DSP-23192). For those versions, we will avoid adding
   * metrics with the prefix below, as they will cause the server to crash.
   */
  private static final String METRICS_FILTER_PREFIX =
      "org.apache.cassandra.metrics.StorageAttachedIndex";
  /** Minimum DSE version for which the above filter does not need to be applied. */
  private static final int MIN_DSE_PATCH_VERSION = 33;

  private static final Pattern VERSION_PATTERN =
      Pattern.compile("([1-9]\\d*)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9]+))?");

  private static final String SERVER_VERSION = FBUtilities.getReleaseVersionString();
  private static final int SERVER_PATCH_VERSION;

  private boolean microLatencyBuckets = false;

  static {
    Matcher matcher = VERSION_PATTERN.matcher(SERVER_VERSION);
    if (matcher.matches()) {
      SERVER_PATCH_VERSION = Integer.parseInt(matcher.group(3));
    } else {
      // unexpected Server version
      logger.warn("Unexpected Server Version string: " + SERVER_VERSION);
      SERVER_PATCH_VERSION = -1;
    }
  }

  private final CassandraMetricNameParser parser;

  private final ConcurrentHashMap<String, RefreshableMetricFamilySamples> familyCache;

  // This cache is used for the remove purpose, we need dropwizardName -> metricName mapping
  private final ConcurrentHashMap<String, String> cache;

  private Method decayingHistogramOffsetMethod = null;

  private Field bucketOffsetField = null;

  public CassandraMetricRegistryListener(
      ConcurrentHashMap<String, RefreshableMetricFamilySamples> familyCache, Configuration config)
      throws NoSuchMethodException {
    parser = CassandraMetricNameParser.getDefaultParser(config);
    cache = new ConcurrentHashMap<>();

    CassandraVersion cassandraVersion = new CassandraVersion(FBUtilities.getReleaseVersionString());

    if (cassandraVersion.major > 4 || (cassandraVersion.major == 4 && cassandraVersion.minor > 0)) {
      // 4.1 and up should use microsecond buckets
      microLatencyBuckets = true;
    }

    this.familyCache = familyCache;
  }

  public void updateCache(
      String dropwizardName, String metricName, RefreshableMetricFamilySamples prototype) {
    // Filter unwanted definitions
    prototype.getDefinitions().removeIf(next -> !next.isKeep());

    if (prototype.getDefinitions().size() < 1) {
      return;
    }

    RefreshableMetricFamilySamples familySamples;
    if (!familyCache.containsKey(metricName)) {
      familyCache.put(metricName, prototype);
      cache.put(dropwizardName, metricName);
    } else {
      familySamples = familyCache.get(metricName);
      prototype.getDefinitions().forEach(familySamples::addDefinition);
    }
  }

  public void removeFromCache(String dropwizardName) {
    String metricName = cache.get(dropwizardName);
    if (metricName == null) {
      return;
    }

    RefreshableMetricFamilySamples familySampler = familyCache.get(metricName);

    familySampler
        .getDefinitions()
        .removeIf(
            cmd ->
                cmd.getMetricName().equals(metricName)
                    || cmd.getMetricName().equals(metricName + "_count")
                    || cmd.getMetricName().equals(metricName + "_total"));

    if (familySampler.getDefinitions().isEmpty()) {
      this.familyCache.remove(metricName);
      cache.remove(dropwizardName);
    }
  }

  private void setGaugeHistogramFiller(Gauge gauge, CassandraMetricDefinition proto) {
    proto.setFiller(
        (samples) -> {
          if (gauge.getValue() == null) {
            logger.trace(String.format("getValue() returned null for %s\n", proto.getMetricName()));
            return;
          }
          long[] inputValues = (long[]) gauge.getValue();
          if (inputValues.length == 0) {
            // Empty
            logger.trace(String.format("Empty inputValues array for %s\n", proto.getMetricName()));
            return;
          }

          final EstimatedHistogram hist = new EstimatedHistogram(inputValues);
          for (int i = 0; i < PRECOMPUTED_QUANTILES.length; i++) {
            List<String> labelValues = new ArrayList<>(proto.getLabelValues().size() + 1);
            int j = 0;
            for (; j < proto.getLabelValues().size(); j++) {
              labelValues.add(j, proto.getLabelValues().get(j));
            }
            labelValues.add(j, PRECOMPUTED_QUANTILES_TEXT[i]);
            Collector.MetricFamilySamples.Sample sample =
                new Collector.MetricFamilySamples.Sample(
                    proto.getMetricName(),
                    proto.getLabelNames(),
                    labelValues,
                    hist.percentile(PRECOMPUTED_QUANTILES[i]));
            samples.add(sample);
          }
        });
  }

  private Supplier<Double> fromGauge(final Gauge<?> gauge) {
    return () -> {
      Object obj = gauge.getValue();
      double value;
      if (obj instanceof Number) {
        value = ((Number) obj).doubleValue();
      } else if (obj instanceof Boolean) {
        value = ((Boolean) obj) ? 1 : 0;
      } else {
        /**
         * These are of type "HashMap<?, ?> and ArrayList<?>", but I haven't found any with actual
         * data on my tests. Add specific parsing later if we find out something valuable is
         * missing.
         */
        return 0.0;
      }

      return value;
    };
  }

  @Override
  public void onGaugeAdded(String dropwizardName, Gauge<?> gauge) {

    if (SERVER_VERSION.startsWith("6.8")) {
      // it's DSE 6.8, see if the version is low enough to require filtering
      if (MIN_DSE_PATCH_VERSION > SERVER_PATCH_VERSION
          && dropwizardName.startsWith(METRICS_FILTER_PREFIX)) {
        // we need to filter
        logger.warn("DSE Server verson is lower than 6.8.33. Filtering metric: " + dropwizardName);
        return;
      }
    }
    try {
      // try to get the gauge
      gauge.getValue();
    } catch (Throwable t) {
      // the gauge wasn't initialized correctly
      logger.warn("Error fetching Gauge value, gauge will be discarded: " + dropwizardName);
      logger.debug("Exception caught fetching gauge", t);
      return;
    }
    if (gauge.getValue() instanceof long[]) {
      // Treat this as a histogram, not gauge
      List<String> additionalLabelNames = new ArrayList<>();
      additionalLabelNames.add("quantile");
      final CassandraMetricDefinition proto =
          parser.parseDropwizardMetric(dropwizardName, "", additionalLabelNames, new ArrayList<>());
      final CassandraMetricDefinition count =
          parser.parseDropwizardMetric(
              dropwizardName, "_count", new ArrayList<>(), new ArrayList<>());

      setGaugeHistogramFiller(gauge, proto);

      count.setValueGetter(
          () -> {
            if (gauge.getValue() == null) {
              return 0.0;
            }
            long[] inputValues = (long[]) gauge.getValue();
            if (inputValues.length == 0) {
              // Empty
              return 0.0;
            }

            // _count
            final EstimatedHistogram hist = new EstimatedHistogram(inputValues);
            return (double) hist.count();
          });

      RefreshableMetricFamilySamples familySamples =
          new RefreshableMetricFamilySamples(
              proto.getMetricName(), Collector.Type.SUMMARY, "", new ArrayList<>());
      familySamples.addDefinition(proto);
      familySamples.addDefinition(count);

      updateCache(dropwizardName, proto.getMetricName(), familySamples);
      return;
    }
    Supplier<Double> gaugeSupplier = fromGauge(gauge);
    CassandraMetricDefinition sample =
        parser.parseDropwizardMetric(dropwizardName, "", new ArrayList<>(), new ArrayList<>());
    sample.setValueGetter(gaugeSupplier);
    RefreshableMetricFamilySamples familySamples =
        new RefreshableMetricFamilySamples(
            sample.getMetricName(), Collector.Type.GAUGE, "", new ArrayList<>());
    familySamples.addDefinition(sample);
    updateCache(dropwizardName, sample.getMetricName(), familySamples);
  }

  @Override
  public void onGaugeRemoved(String name) {
    removeFromCache(name);
  }

  @Override
  public void onCounterAdded(String name, Counter counter) {
    Supplier<Double> getValue = () -> (double) counter.getCount();
    CassandraMetricDefinition sampler =
        parser.parseDropwizardMetric(name, "", new ArrayList<>(), new ArrayList<>());
    sampler.setValueGetter(getValue);
    RefreshableMetricFamilySamples familySamples =
        new RefreshableMetricFamilySamples(
            sampler.getMetricName(), Collector.Type.GAUGE, "", new ArrayList<>());
    familySamples.addDefinition(sampler);
    updateCache(name, sampler.getMetricName(), familySamples);
  }

  @Override
  public void onCounterRemoved(String name) {
    removeFromCache(name);
  }

  @Override
  public void onHistogramAdded(String dropwizardName, Histogram histogram) {
    List<String> additionalLabelNames = new ArrayList<>();
    additionalLabelNames.add("quantile");
    final CassandraMetricDefinition proto =
        parser.parseDropwizardMetric(dropwizardName, "", additionalLabelNames, new ArrayList<>());
    final CassandraMetricDefinition count =
        parser.parseDropwizardMetric(
            dropwizardName, "_count", new ArrayList<>(), new ArrayList<>());
    Supplier<Double> countSupplier = () -> (double) histogram.getCount();

    RefreshableMetricFamilySamples familySamples =
        new RefreshableMetricFamilySamples(
            proto.getMetricName(), Collector.Type.SUMMARY, "", new ArrayList<>());
    setHistogramFiller(histogram, proto, 1.0);
    count.setValueGetter(countSupplier);
    familySamples.addDefinition(proto);
    familySamples.addDefinition(count);

    updateCache(dropwizardName, proto.getMetricName(), familySamples);
  }

  private static void setHistogramFiller(
      Histogram histogram, CassandraMetricDefinition proto, double factor) {
    proto.setFiller(
        (samples) -> {
          Snapshot snapshot = histogram.getSnapshot();
          double[] values =
              new double[] {
                snapshot.getMedian(),
                snapshot.get75thPercentile(),
                snapshot.get95thPercentile(),
                snapshot.get98thPercentile(),
                snapshot.get99thPercentile(),
                snapshot.get999thPercentile()
              };
          for (int i = 0; i < PRECOMPUTED_QUANTILES.length; i++) {
            List<String> labelValues = new ArrayList<>(proto.getLabelValues().size() + 1);
            int j = 0;
            for (; j < proto.getLabelValues().size(); j++) {
              labelValues.add(j, proto.getLabelValues().get(j));
            }
            labelValues.add(j, PRECOMPUTED_QUANTILES_TEXT[i]);
            Collector.MetricFamilySamples.Sample sample =
                new Collector.MetricFamilySamples.Sample(
                    proto.getMetricName(), proto.getLabelNames(), labelValues, values[i] * factor);
            samples.add(sample);
          }
        });
  }

  @Override
  public void onHistogramRemoved(String dropwizardName) {
    removeFromCache(dropwizardName);
  }

  @Override
  public void onMeterAdded(String name, Meter meter) {
    Supplier<Double> getValue = () -> (double) meter.getCount();
    CassandraMetricDefinition total =
        parser.parseDropwizardMetric(name, "_total", new ArrayList<>(), new ArrayList<>());
    total.setValueGetter(getValue);

    RefreshableMetricFamilySamples familySamples =
        new RefreshableMetricFamilySamples(
            total.getMetricName(), Collector.Type.COUNTER, "", new ArrayList<>());
    familySamples.addDefinition(total);
    updateCache(name, total.getMetricName(), familySamples);
  }

  @Override
  public void onMeterRemoved(String name) {
    removeFromCache(name);
  }

  private void setTimerFiller(
      Timer timer,
      CassandraMetricDefinition proto,
      CassandraMetricDefinition bucket,
      CassandraMetricDefinition count,
      CassandraMetricDefinition sum) {
    proto.setFiller(
        (samples) -> {
          Snapshot snapshot = timer.getSnapshot();

          long[] buckets = CassandraMetricsTools.INPUT_BUCKETS;
          long[] values = snapshot.getValues();
          String snapshotClass = snapshot.getClass().getName();

          if (snapshotClass.contains("EstimatedHistogramReservoirSnapshot")) {
            // OSS versions
            try {
              if (bucketOffsetField == null) {
                bucketOffsetField =
                    snapshot.getClass().getSuperclass().getDeclaredField("bucketOffsets");
                bucketOffsetField.setAccessible(true);
              }
              buckets = (long[]) bucketOffsetField.get(snapshot);
            } catch (NoSuchFieldException | IllegalAccessException e) {
              buckets = CassandraMetricsTools.DECAYING_BUCKETS;
            }

          } else if (snapshotClass.contains("DecayingEstimatedHistogram")) {
            // DSE
            try {
              if (decayingHistogramOffsetMethod == null) {
                decayingHistogramOffsetMethod = snapshot.getClass().getMethod("getOffsets");
              }

              buckets = (long[]) decayingHistogramOffsetMethod.invoke(snapshot);
            } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
              logger.debug(
                  String.format("Unable to getOffsets for DSE, snapshotClass: %s", snapshotClass),
                  e);
            }
          } else {
            logger.debug(
                String.format(
                    "Unknown type for %s, wants: %s\n", proto.getMetricName(), snapshotClass));
          }

          // This can happen if histogram isn't EstimatedDecay or EstimatedHistogram
          if (values.length > buckets.length + 1 || values.length < buckets.length) {
            logger.error(
                String.format(
                    "Values and bucket lengths do not match: %d != %d. SnapshotClass: %s, metric: %s",
                    values.length, buckets.length, snapshotClass, proto.getMetricName()));
            return;
          }

          int outputIndex = 0; // output index
          long cumulativeCount = 0;
          for (int i = 0; i < buckets.length; i++) {
            int offsetFix = microLatencyBuckets ? 1 : 1000;
            if (outputIndex < LATENCY_OFFSETS.length
                && buckets[i] > (LATENCY_OFFSETS[outputIndex] * offsetFix)) {
              List<String> labelValues = new ArrayList<>(bucket.getLabelValues().size() + 1);
              int j = 0;
              for (; j < bucket.getLabelValues().size(); j++) {
                labelValues.add(j, bucket.getLabelValues().get(j));
              }
              labelValues.add(j, LATENCY_OFFSETS_TEXT[outputIndex++]);
              Collector.MetricFamilySamples.Sample sample =
                  new Collector.MetricFamilySamples.Sample(
                      bucket.getMetricName(), bucket.getLabelNames(), labelValues, cumulativeCount);
              samples.add(sample);
            }

            cumulativeCount += values[i];
          }

          // Add any remaining buckets that didn't have any values
          while (outputIndex++ < LATENCY_OFFSETS.length) {
            List<String> labelValues = new ArrayList<>(bucket.getLabelValues().size() + 1);
            int j = 0;
            for (; j < bucket.getLabelValues().size(); j++) {
              labelValues.add(j, bucket.getLabelValues().get(j));
            }
            labelValues.add(j, LATENCY_OFFSETS_TEXT[outputIndex]);
            Collector.MetricFamilySamples.Sample sample =
                new Collector.MetricFamilySamples.Sample(
                    bucket.getMetricName(), bucket.getLabelNames(), labelValues, cumulativeCount);
            samples.add(sample);
          }

          // Last bucket must be +Inf and same as _count
          List<String> labelValues = new ArrayList<>(bucket.getLabelValues().size() + 1);
          int j = 0;
          for (; j < bucket.getLabelValues().size(); j++) {
            labelValues.add(j, bucket.getLabelValues().get(j));
          }
          labelValues.add(j, INF_BUCKET);
          Collector.MetricFamilySamples.Sample sample =
              new Collector.MetricFamilySamples.Sample(
                  bucket.getMetricName(), bucket.getLabelNames(), labelValues, cumulativeCount);
          samples.add(sample);

          /**
           * Add sum by calculating it from the mean. This isn't exact, but it's the only exposed
           * way
           */
          double sumValue = snapshot.getMean() * cumulativeCount;

          if (values.length > buckets.length && values[buckets.length] > 0) {
            // If last bucket has data, it means the histogram has overflowed
            sumValue = Long.MAX_VALUE;
          }

          Collector.MetricFamilySamples.Sample sumSample =
              new Collector.MetricFamilySamples.Sample(
                  sum.getMetricName(), sum.getLabelNames(), sum.getLabelValues(), sumValue);
          samples.add(sumSample);

          Collector.MetricFamilySamples.Sample countSample =
              new Collector.MetricFamilySamples.Sample(
                  count.getMetricName(),
                  count.getLabelNames(),
                  count.getLabelValues(),
                  cumulativeCount);
          samples.add(countSample);
        });
  }

  @Override
  public void onTimerAdded(String dropwizardName, Timer timer) {
    List<String> additionalLabelNames = new ArrayList<>();
    additionalLabelNames.add(QUANTILE_LABEL_NAME);
    List<String> additionalBucketLabel = new ArrayList<>();
    additionalBucketLabel.add(BUCKET_LABEL_NAME);
    final CassandraMetricDefinition proto =
        parser.parseDropwizardMetric(dropwizardName, "", additionalLabelNames, new ArrayList<>());
    final CassandraMetricDefinition buckets =
        parser.parseDropwizardMetric(
            dropwizardName, "_bucket", additionalBucketLabel, new ArrayList<>());
    final CassandraMetricDefinition count =
        parser.parseDropwizardMetric(
            dropwizardName, "_count", new ArrayList<>(), new ArrayList<>());
    final CassandraMetricDefinition sum =
        parser.parseDropwizardMetric(dropwizardName, "_sum", new ArrayList<>(), new ArrayList<>());

    setTimerFiller(timer, proto, buckets, count, sum);

    RefreshableMetricFamilySamples familySamples =
        new RefreshableMetricFamilySamples(
            proto.getMetricName(), Collector.Type.HISTOGRAM, "", new ArrayList<>());
    familySamples.addDefinition(proto);

    updateCache(dropwizardName, proto.getMetricName(), familySamples);
  }

  @Override
  public void onTimerRemoved(String name) {
    onHistogramRemoved(name);
  }
}
