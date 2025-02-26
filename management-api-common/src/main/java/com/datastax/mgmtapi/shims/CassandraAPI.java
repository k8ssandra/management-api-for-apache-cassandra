/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.shims;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.cassandra.auth.INetworkAuthorizer;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.compaction.CompactionManager;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.hints.HintsService;
import org.apache.cassandra.locator.SeedProvider;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.transport.Server;

/** Place to abstract C* apis that change across versions */
public interface CassandraAPI {

  public default void enableFullQuerylog() {
    throw new UnsupportedOperationException("FQL is only supported on OSS Cassandra > 4x.");
  }

  public default void disableFullQuerylog() {
    throw new UnsupportedOperationException("FQL is only supported on OSS Cassandra > 4x.");
  }

  public default boolean isFullQueryLogEnabled() {
    throw new UnsupportedOperationException("FQL is only supported on OSS Cassandra > 4x.");
  }

  void decommission(boolean force) throws InterruptedException;

  default void rebuild(String srcDc) {
    getStorageService().rebuild(srcDc);
  }

  Map<List<Long>, List<String>> checkConsistencyLevel(String consistencyLevelName, Integer rfPerDc);

  SeedProvider getK8SeedProvider();

  Set<InetAddress> reloadSeeds();

  ChannelInitializer<Channel> makeSocketInitializer(
      final Server.ConnectionTracker connectionTracker);

  List<Map<String, String>> getEndpointStates();

  List<Map<String, List<Map<String, String>>>> getStreamInfo();

  default UntypedResultSet processQuery(String query, ConsistencyLevel consistencyLevel) {
    return QueryProcessor.process(query, consistencyLevel);
  }

  StorageService getStorageService();

  IRoleManager getRoleManager();

  CompactionManager getCompactionManager();

  Gossiper getGossiper();

  default Object handleRpcResult(Callable<Object> rpcResult) throws Exception {
    return rpcResult.call();
  }

  String getLocalDataCenter();

  RpcStatementShim makeRpcStatement(String method, String[] params);

  HintsService getHintsService();

  default List<String> getKeyspaces() {
    return StorageService.instance.getKeyspaces();
  }

  default void reloadInternodeEncryptionTruststore() throws Exception {
    throw new UnsupportedOperationException("Unimplemented for Cassandra, only available for DSE");
  };

  default List<Map<String, String>> listRoles() {
    IRoleManager roleManager = getRoleManager();
    Set<RoleResource> allRoles = roleManager.getAllRoles();
    List<Map<String, String>> roles = new ArrayList<>();
    for (RoleResource role : allRoles) {
      Map<String, String> roleOutput = new HashMap<>();
      roleOutput.put("name", role.getRoleName());
      roleOutput.put("super", String.valueOf(roleManager.isSuper(role)));
      roleOutput.put("login", String.valueOf(roleManager.canLogin(role)));

      Map<String, String> customOptions = roleManager.getCustomOptions(role);
      // The driver has similar code, but that's private
      String optionsAsString =
          customOptions.keySet().stream()
              .map(key -> key + ": " + customOptions.get(key))
              .collect(Collectors.joining(", ", "{", "}"));

      roleOutput.put("options", optionsAsString);

      INetworkAuthorizer networkAuthorizer = DatabaseDescriptor.getNetworkAuthorizer();
      roleOutput.put("datacenters", networkAuthorizer.authorize(role).toString());
      roles.add(roleOutput);
    }

    return roles;
  }

  default Collection<Token> getTokens() {
    return StorageService.instance.getTokenMetadata().sortedTokens();
  }
}
