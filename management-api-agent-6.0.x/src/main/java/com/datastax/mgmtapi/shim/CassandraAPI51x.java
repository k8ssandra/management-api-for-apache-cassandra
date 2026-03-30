/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.shim;

import com.datastax.mgmtapi.shims.CassandraAPI;
import com.datastax.mgmtapi.shims.RpcStatementShim;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.compaction.CompactionManager;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.fql.FullQueryLoggerOptions;
import org.apache.cassandra.gms.ApplicationState;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.gms.TokenSerializer;
import org.apache.cassandra.gms.VersionedValue;
import org.apache.cassandra.hints.HintsService;
import org.apache.cassandra.locator.AbstractReplicationStrategy;
import org.apache.cassandra.locator.Endpoints;
import org.apache.cassandra.locator.EndpointsForRange;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.locator.K8SeedProvider51x;
import org.apache.cassandra.locator.ReplicaPlans;
import org.apache.cassandra.locator.SeedProvider;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.KeyspaceParams;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.streaming.SessionInfo;
import org.apache.cassandra.streaming.StreamManager;
import org.apache.cassandra.streaming.StreamState;
import org.apache.cassandra.streaming.management.StreamStateCompositeData;
import org.apache.cassandra.tcm.ClusterMetadata;
import org.apache.cassandra.tcm.ClusterMetadataService;
import org.apache.cassandra.tcm.compatibility.TokenRingUtils;
import org.apache.cassandra.tcm.membership.Location;
import org.apache.cassandra.tcm.membership.NodeId;
import org.apache.cassandra.transport.Server;
import org.apache.cassandra.transport.UnixSocketServer51x;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.JVMStabilityInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraAPI51x implements CassandraAPI {
  private static final Logger logger = LoggerFactory.getLogger(CassandraAPI51x.class);

  private static final Supplier<SeedProvider> seedProvider =
      Suppliers.memoize(() -> new K8SeedProvider51x());

  @Override
  public void enableFullQuerylog() {
    logger.debug("Getting FQL options and calling enableFullQueryLogger.");
    FullQueryLoggerOptions fqlOpts = DatabaseDescriptor.getFullQueryLogOptions();
    StorageService.instance.enableFullQueryLogger(
        fqlOpts.log_dir,
        fqlOpts.roll_cycle,
        fqlOpts.block,
        fqlOpts.max_queue_weight,
        fqlOpts.max_log_size,
        fqlOpts.archive_command,
        fqlOpts.max_archive_retries);
  }

  @Override
  public void disableFullQuerylog() {
    logger.debug("Stopping FullQueryLogger.");
    StorageService.instance.stopFullQueryLogger();
  }

  @Override
  public boolean isFullQueryLogEnabled() {
    boolean isEnabled = StorageService.instance.isFullQueryLogEnabled();
    logger.debug("Querying whether full query logging is enabled. Result is {}", isEnabled);
    return isEnabled;
  }

  @Override
  public void decommission(boolean force) throws InterruptedException {
    StorageService.instance.decommission(force);
  }

  @Override
  public Map<List<Long>, List<String>> checkConsistencyLevel(
      String consistencyLevelName, Integer rfPerDc) {
    try {
      IPartitioner partitioner = DatabaseDescriptor.getPartitioner();
      ClusterMetadata clusterMetadata = ClusterMetadataService.instance().metadata();

      ConsistencyLevel cl = ConsistencyLevel.valueOf(consistencyLevelName);

      Map<String, String> dcNames = getDcNamesFromDbDescriptor(clusterMetadata, rfPerDc);

      Keyspace mockKs =
          Keyspace.mockKS(
              KeyspaceMetadata.create(
                  "none",
                  KeyspaceParams.create(
                      true,
                      ImmutableMap.<String, String>builder()
                          .put("class", "NetworkTopologyStrategy")
                          .putAll(dcNames)
                          .build())));

      AbstractReplicationStrategy mockStrategy = mockKs.getReplicationStrategy();
      mockStrategy.validateOptions();

      List<Range<Token>> tokenRanges =
          TokenRingUtils.getAllRanges(clusterMetadata.tokenMap.tokens());

      Map<List<Long>, List<String>> results = new HashMap<>();

      // For each range check the endpoints can achieve cl using the midpoint
      for (Range<Token> range : tokenRanges) {
        Token midpoint = partitioner.midpoint(range.left, range.right);
        EndpointsForRange endpoints =
            mockStrategy.calculateNaturalReplicas(midpoint, clusterMetadata);

        if (!this.isSufficientLiveReplicasForRead(mockKs.getReplicationStrategy(), cl, endpoints)) {
          List<String> downEndpoints = new ArrayList<>();
          for (InetAddressAndPort endpoint : endpoints.endpoints()) {
            EndpointState epState = Gossiper.instance.getEndpointStateForEndpoint(endpoint);

            if (!epState.isAlive()) downEndpoints.add(endpoint.toString());
          }

          int blockFor = cl.blockFor(mockKs.getReplicationStrategy());

          if (downEndpoints.isEmpty() && endpoints.size() < blockFor)
            downEndpoints.add(
                String.format(
                    "%d replicas required, but only %d nodes in the ring",
                    blockFor, endpoints.size()));
          else if (downEndpoints.isEmpty()) downEndpoints.add("Nodes Flapping");

          results.put(
              ImmutableList.of(
                  (long) range.left.getTokenValue(), (long) range.right.getTokenValue()),
              downEndpoints);
        }
      }
      return results;
    } catch (Throwable e) {
      logger.error("Exception encountered", e);
      throw e;
    }
  }

  private Map<String, String> getDcNamesFromDbDescriptor(
      ClusterMetadata clusterMetadata, Integer rfPerDc) {
    Map<String, String> dcNames = new HashMap<>();
    try {
      // see if we have the newer getLocator() method
      try {
        Class locatorClass = Class.forName("org.apache.cassandra.locator.Locator");
        Method getLocator = DatabaseDescriptor.class.getDeclaredMethod("getLocator", null);
        Object locatorObj = getLocator.invoke(null, null);
        Method locationMethod =
            locatorClass.getDeclaredMethod("location", InetAddressAndPort.class);
        for (Map.Entry<Token, NodeId> en : clusterMetadata.tokenMap.asMap().entrySet()) {
          InetAddressAndPort endpoint = clusterMetadata.directory.endpoint(en.getValue());
          Object locator = locatorClass.cast(getLocator.invoke(null, null));
          Location location = (Location) locationMethod.invoke(locator, endpoint);
          String dc = location.datacenter;
          assert dc != null;

          dcNames.put(dc, String.valueOf(rfPerDc));
        }

      } catch (ClassNotFoundException | NoSuchMethodException ex) {
        // try the older getEndpointSnitch() method
        Method getEndpointSnitch =
            DatabaseDescriptor.class.getDeclaredMethod("getEndpointSnitch", null);
        IEndpointSnitch endpointSnitch = (IEndpointSnitch) (getEndpointSnitch.invoke(null, null));
        for (Map.Entry<Token, NodeId> en : clusterMetadata.tokenMap.asMap().entrySet()) {
          InetAddressAndPort endpoint = clusterMetadata.directory.endpoint(en.getValue());
          String dc = endpointSnitch.getDatacenter(endpoint);
          assert dc != null;

          dcNames.put(dc, String.valueOf(rfPerDc));
        }
      }
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
    return dcNames;
  }

  private boolean isSufficientLiveReplicasForRead(
      AbstractReplicationStrategy replicationStrategy,
      ConsistencyLevel consistencyLevel,
      Endpoints<?> liveReplicas) {
    try {
      try {
        // see if we need the Locator
        Class locatorClass = Class.forName("org.apache.cassandra.locator.Locator");
        Method getLocator = DatabaseDescriptor.class.getDeclaredMethod("getLocator", null);
        Method isSufficientLiveReplicasForRead =
            ReplicaPlans.class.getDeclaredMethod(
                "isSufficientLiveReplicasForRead",
                locatorClass,
                AbstractReplicationStrategy.class,
                ConsistencyLevel.class,
                Endpoints.class);
        return (boolean)
            (isSufficientLiveReplicasForRead.invoke(
                null,
                locatorClass.cast(getLocator.invoke(null, null)),
                replicationStrategy,
                consistencyLevel,
                liveReplicas));
      } catch (NoSuchMethodException ex) {
        // try without Locator
        Method isSufficientLiveReplicasForRead =
            ReplicaPlans.class.getDeclaredMethod(
                "isSufficientLiveReplicasForRead",
                AbstractReplicationStrategy.class,
                ConsistencyLevel.class,
                Endpoints.class);
        return (boolean)
            (isSufficientLiveReplicasForRead.invoke(
                null, replicationStrategy, consistencyLevel, liveReplicas));
      }
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Override
  public SeedProvider getK8SeedProvider() {
    return seedProvider.get();
  }

  public Set<InetAddress> reloadSeeds() {
    Field seedField = FBUtilities.getProtectedField(Gossiper.class, "seeds");

    Set<InetAddressAndPort> seeds = null;
    try {
      seeds = (Set<InetAddressAndPort>) seedField.get(Gossiper.instance);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    // Get the new set in the same that buildSeedsList does
    Set<InetAddressAndPort> tmp = new HashSet<>();
    try {
      for (InetAddressAndPort seed : getK8SeedProvider().getSeeds()) {
        if (seed.equals(FBUtilities.getBroadcastAddressAndPort())) continue;
        tmp.add(seed);
      }
    }
    /**
     * If using the SimpleSeedProvider invalid yaml added to the config since startup could cause
     * this to throw. Additionally, third party seed providers may throw exceptions. Handle the
     * error and return a null to indicate that there was a problem.
     */
    catch (Throwable e) {
      JVMStabilityInspector.inspectThrowable(e);
      return null;
    }

    if (tmp.size() == 0) {
      return seeds.stream().map(s -> s.getAddress()).collect(Collectors.toSet());
    }

    if (tmp.equals(seeds)) {
      return seeds.stream().map(s -> s.getAddress()).collect(Collectors.toSet());
    }

    // Add the new entries
    seeds.addAll(tmp);
    // Remove the old entries
    seeds.retainAll(tmp);
    logger.debug("New seed node list after reload {}", seeds);

    return seeds.stream().map(s -> s.getAddress()).collect(Collectors.toSet());
  }

  @Override
  public ChannelInitializer<Channel> makeSocketInitializer(
      Server.ConnectionTracker connectionTracker) {
    return UnixSocketServer51x.makeSocketInitializer(connectionTracker);
  }

  @Override
  public List<Map<String, String>> getEndpointStates() {
    List<Map<String, String>> result = new ArrayList<>();

    IPartitioner partitioner = DatabaseDescriptor.getPartitioner();

    for (InetAddressAndPort endpoint : ClusterMetadata.current().directory.allJoinedEndpoints()) {
      EndpointState state = Gossiper.instance.getEndpointStateForEndpoint(endpoint);
      Map<String, String> states = new HashMap<>();
      for (Map.Entry<ApplicationState, VersionedValue> s : state.states()) {
        String value =
            (s.getKey() == ApplicationState.TOKENS)
                ? formatTokens(partitioner, s)
                : s.getValue().value;
        states.put(s.getKey().name(), value);
      }

      states.put("ENDPOINT_IP", endpoint.getHostAddress(false));
      states.put("IS_ALIVE", Boolean.toString(state.isAlive()));
      states.put("PARTITIONER", partitioner.getClass().getName());
      states.put("CLUSTER_NAME", getStorageService().getClusterName());
      states.put(
          "IS_LOCAL", Boolean.toString(endpoint.equals(FBUtilities.getBroadcastAddressAndPort())));
      result.add(states);
    }

    return result;
  }

  private String formatTokens(
      IPartitioner partitioner, Map.Entry<ApplicationState, VersionedValue> s) {
    try {
      byte[] bytes = s.getValue().value.getBytes(StandardCharsets.ISO_8859_1);
      Collection<Token> tokens =
          TokenSerializer.deserialize(
              partitioner, new DataInputStream(new ByteArrayInputStream(bytes)));
      return tokens.stream().map(Token::toString).collect(Collectors.joining(","));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public List<Map<String, List<Map<String, String>>>> getStreamInfo() {
    Set<StreamState> streams =
        StreamManager.instance.getCurrentStreams().stream()
            .map(StreamStateCompositeData::fromCompositeData)
            .collect(Collectors.toSet());

    List<Map<String, List<Map<String, String>>>> result = new ArrayList<>();

    for (StreamState status : streams) {
      Map<String, List<Map<String, String>>> streamInfo = new HashMap<>();
      List<Map<String, String>> sessionResults = new ArrayList<>();

      for (SessionInfo info : status.sessions) {
        Map<String, String> sessionInfo = new HashMap<>();
        sessionInfo.put("STREAM_OPERATION", status.streamOperation.getDescription());
        sessionInfo.put("PEER", info.peer.toString());
        sessionInfo.put("USING_CONNECTION", info.connecting.toString());
        sessionInfo.put("TOTAL_FILES_TO_RECEIVE", String.valueOf(info.getTotalFilesToReceive()));
        sessionInfo.put("TOTAL_FILES_RECEIVED", String.valueOf(info.getTotalFilesReceived()));
        sessionInfo.put("TOTAL_SIZE_TO_RECEIVE", String.valueOf(info.getTotalSizeToReceive()));
        sessionInfo.put("TOTAL_SIZE_RECEIVED", String.valueOf(info.getTotalSizeReceived()));

        sessionInfo.put("TOTAL_FILES_TO_SEND", String.valueOf(info.getTotalFilesToSend()));
        sessionInfo.put("TOTAL_FILES_SENT", String.valueOf(info.getTotalFilesSent()));
        sessionInfo.put("TOTAL_SIZE_TO_SEND", String.valueOf(info.getTotalSizeToSend()));
        sessionInfo.put("TOTAL_SIZE_SENT", String.valueOf(info.getTotalSizeSent()));
        sessionResults.add(sessionInfo);
      }

      streamInfo.put(status.planId.toString(), sessionResults);

      result.add(streamInfo);
    }

    return result;
  }

  @Override
  public StorageService getStorageService() {
    return StorageService.instance;
  }

  @Override
  public IRoleManager getRoleManager() {
    return DatabaseDescriptor.getRoleManager();
  }

  @Override
  public CompactionManager getCompactionManager() {
    return CompactionManager.instance;
  }

  @Override
  public Gossiper getGossiper() {
    return Gossiper.instance;
  }

  @Override
  public String getLocalDataCenter() {
    return DatabaseDescriptor.getLocalDataCenter();
  }

  @Override
  public RpcStatementShim makeRpcStatement(String method, String[] params) {
    return new RpcStatement51x(method, params);
  }

  @Override
  public HintsService getHintsService() {
    return HintsService.instance;
  }

  @Override
  public Collection<Token> getTokens() {
    return ClusterMetadataService.instance().metadata().tokenMap.tokens();
  }
}
