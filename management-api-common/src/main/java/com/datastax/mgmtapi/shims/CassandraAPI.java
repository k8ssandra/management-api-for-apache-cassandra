package com.datastax.mgmtapi.shims;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.apache.cassandra.locator.SeedProvider;
import org.apache.cassandra.transport.Server;

/**
 * Place to abstract C* apis that change across versions
 */
public interface CassandraAPI
{
    void decommission(boolean force) throws InterruptedException;

    Map<List<Long>, List<String>> checkConsistencyLevel(String consistencyLevelName, Integer rfPerDc);

    SeedProvider getK8SeedProvider();

    Set<InetAddress> reloadSeeds();

    ChannelInitializer<Channel> makeSocketInitializer(final Server.ConnectionTracker connectionTracker);

    List<Map<String,String>> getEndpointStates();
}
