/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.shims;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.compaction.CompactionManager;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.hints.HintsService;
import org.apache.cassandra.locator.SeedProvider;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.transport.Server;

/**
 * Place to abstract C* apis that change across versions
 */
public interface CassandraAPI
{
    
    default public void enableFullQuerylog()
    {
        throw new UnsupportedOperationException("FQL is only supported on OSS Cassandra > 4x.");
    }
    
    default public void disableFullQuerylog() 
    {
        throw new UnsupportedOperationException("FQL is only supported on OSS Cassandra > 4x.");
    }
    
    default public boolean isFullQueryLogEnabled() 
    {
        throw new UnsupportedOperationException("FQL is only supported on OSS Cassandra > 4x.");
        
    }

    void decommission(boolean force) throws InterruptedException;

    Map<List<Long>, List<String>> checkConsistencyLevel(String consistencyLevelName, Integer rfPerDc);

    SeedProvider getK8SeedProvider();

    Set<InetAddress> reloadSeeds();

    ChannelInitializer<Channel> makeSocketInitializer(final Server.ConnectionTracker connectionTracker);

    List<Map<String,String>> getEndpointStates();

    List<Map<String, List<Map<String, String>>>> getStreamInfo();

    default UntypedResultSet processQuery(String query, ConsistencyLevel consistencyLevel)
    {
        return QueryProcessor.process(query, consistencyLevel);
    }

    StorageService getStorageService();

    IRoleManager getRoleManager();

    CompactionManager getCompactionManager();

    Gossiper getGossiper();

    default Object handleRpcResult(Callable<Object> rpcResult) throws Exception
    {
        return rpcResult.call();
    }

    String getLocalDataCenter();

    RpcStatementShim makeRpcStatement(String method, String[] params);

    HintsService getHintsService();

    default List<String> getKeyspaces() {
        return StorageService.instance.getKeyspaces();
    }
}
