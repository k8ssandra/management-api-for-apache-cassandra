package io.k8ssandra.metrics.builder;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.service.StorageService;

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

    public final static String CLUSTER_NAME = getClusterName();
    public final static String RACK_NAME = getRack();
    public final static String DATACENTER_NAME = getDatacenter();
    public final static String POD_NAME = getBroadcastAddress().getHostAddress();
    public final static String HOST_ID = getHostId();

    public final static List<String> DEFAULT_LABEL_NAMES = Arrays.asList(HOSTID_LABEL_NAME, INSTANCE_LABEL_NAME, CLUSTER_LABEL_NAME, DATACENTER_LABEL_NAME, RACK_LABEL_NAME);
    public final static List<String> DEFAULT_LABEL_VALUES = Arrays.asList(HOST_ID, POD_NAME, CLUSTER_NAME, DATACENTER_NAME, RACK_NAME);

    public static final Double[] PRECOMPUTED_QUANTILES = new Double[]{0.5, 0.75, 0.95, 0.98, 0.99, 0.999};

    // This is to reduce allocations
    public static final String[] PRECOMPUTED_QUANTILES_TEXT = new String[PRECOMPUTED_QUANTILES.length];

    static {
        for (int i = 0; i < PRECOMPUTED_QUANTILES.length; i++) {
            PRECOMPUTED_QUANTILES_TEXT[i] = PRECOMPUTED_QUANTILES[i].toString();
        }
    }

    private Map<String, CassandraMetricDefinition> metricDefinitions;

    public CassandraMetricsTools() {
        metricDefinitions = new HashMap<>();
    }

    public static String getHostId() {
        try {
            return StorageService.instance.getLocalHostId();
        } catch (NullPointerException npe) {
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
