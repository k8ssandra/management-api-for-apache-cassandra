package io.k8ssandra.metrics.builder;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.EstimatedHistogram;
import org.apache.cassandra.utils.Pair;

import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CassandraMetricsTools {

    public final static String CLUSTER_LABEL_NAME = "cluster";
    public final static String DATACENTER_LABEL_NAME = "datacenter";
    public final static String INSTANCE_LABEL_NAME = "instance";
    public final static String RACK_LABEL_NAME = "rack";
    public final static String HOSTID_LABEL_NAME = "host";
    public final static String BUCKET_LABEL_NAME = "le";
    public final static String QUANTILE_LABEL_NAME = "quantile";

    public final static String CLUSTER_NAME = getClusterName();
    public final static String RACK_NAME = getRack();
    public final static String DATACENTER_NAME = getDatacenter();
    public final static String INSTANCE_NAME = getBroadcastAddress().getHostAddress();
    public final static String HOST_ID = getHostId();
    public final static String INF_BUCKET = "+Inf";

    public final static List<String> DEFAULT_LABEL_NAMES = Arrays.asList(HOSTID_LABEL_NAME, INSTANCE_LABEL_NAME, CLUSTER_LABEL_NAME, DATACENTER_LABEL_NAME, RACK_LABEL_NAME);
    public final static List<String> DEFAULT_LABEL_VALUES = Arrays.asList(HOST_ID, INSTANCE_NAME, CLUSTER_NAME, DATACENTER_NAME, RACK_NAME);

    public static final double[] PRECOMPUTED_QUANTILES = new double[]{0.5, 0.75, 0.95, 0.98, 0.99, 0.999};

    // This is to reduce allocations
    public static final String[] PRECOMPUTED_QUANTILES_TEXT = new String[PRECOMPUTED_QUANTILES.length];

    static {
        for (int i = 0; i < PRECOMPUTED_QUANTILES.length; i++) {
            PRECOMPUTED_QUANTILES_TEXT[i] = Double.valueOf(PRECOMPUTED_QUANTILES[i]).toString();
        }
    }

    // These buckets are using the previous MCAC / collectd compatible values
    protected static final long[] INPUT_BUCKETS = new EstimatedHistogram(90).getBucketOffsets();
    protected static final long[] DECAYING_BUCKETS = new EstimatedHistogram(165).getBucketOffsets();

    // Log linear buckets (these must match the collectd entry in types.db)
    protected static final Pair<Long, String>[] LATENCY_BUCKETS;
    protected static final long[] LATENCY_OFFSETS = { 35, 60, 103, 179, 310, 535, 924, 1597, 2759, 4768, 8239, 14237,
            24601, 42510, 73457, 126934, 219342, 379022, 654949, 1131752, 1955666, 3379391, 5839588, 10090808,
            17436917 };

    protected static final String[] LATENCY_OFFSETS_TEXT = new String[LATENCY_OFFSETS.length];

    static {
        for(int i = 0; i < LATENCY_OFFSETS.length; i++) {
            LATENCY_OFFSETS_TEXT[i] = Long.valueOf(LATENCY_OFFSETS[i]).toString();
        }
    }

    static {
        LATENCY_BUCKETS = new Pair[LATENCY_OFFSETS.length];
        for (int i = 0; i < LATENCY_BUCKETS.length; i++) {
            // Latencies are reported in nanoseconds, so we convert the offsets from micros
            // to nanos
            LATENCY_BUCKETS[i] = Pair.create(LATENCY_OFFSETS[i] * 1000, "bucket_" + Long.toString(LATENCY_OFFSETS[i]));
        }
    }

    private Map<String, CassandraMetricDefinition> metricDefinitions;

    public CassandraMetricsTools() {
        metricDefinitions = new HashMap<>();
    }

    public static String getHostId() {
        try {
            return StorageService.instance.getLocalHostId();
        } catch (ExceptionInInitializerError e) {
            return "123456789";
        }
    }

    public static String getClusterName() {
        try {
            return DatabaseDescriptor.getClusterName();
        } catch (NullPointerException npe) {
            return "Test Cluster";
        }
    }

    public static String getRack() {
        try {
            return (String) IEndpointSnitch.class.getMethod("getLocalRack")
                    .invoke(DatabaseDescriptor.getEndpointSnitch());
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | NullPointerException |
                 IllegalAccessException e) {
            // No biggie
        }

        try {
            return (String) IEndpointSnitch.class.getMethod("getRack", InetAddress.class).invoke(DatabaseDescriptor.getEndpointSnitch(), getBroadcastAddress());
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | NullPointerException |
                 SecurityException e) {
            return "unknown_rack";
        }
    }

    public static InetAddress getBroadcastAddress() {
        try {
            return DatabaseDescriptor.getBroadcastAddress() == null
                    ? DatabaseDescriptor.getListenAddress() == null ? InetAddress.getLocalHost()
                    : DatabaseDescriptor.getListenAddress()
                    : DatabaseDescriptor.getBroadcastAddress();
        } catch (UnknownHostException | NullPointerException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getDatacenter() {
        try {
            return (String) IEndpointSnitch.class.getMethod("getLocalDatacenter").invoke(DatabaseDescriptor.getEndpointSnitch());
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | NullPointerException |
                 IllegalAccessException e) {
            //No biggie
        }

        try {
            return (String) IEndpointSnitch.class.getMethod("getDatacenter", InetAddress.class).invoke(DatabaseDescriptor.getEndpointSnitch(), getBroadcastAddress());
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | NullPointerException |
                 SecurityException e) {
            return "unknown_dc";
        }
    }
}
