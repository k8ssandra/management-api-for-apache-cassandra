/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.shim;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.shims.CassandraAPI;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.gms.ApplicationState;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.gms.VersionedValue;
import org.apache.cassandra.locator.AbstractReplicationStrategy;
import org.apache.cassandra.locator.EndpointsForRange;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.locator.K8SeedProvider4x;
import org.apache.cassandra.locator.ReplicaPlans;
import org.apache.cassandra.locator.SeedProvider;
import org.apache.cassandra.locator.TokenMetadata;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.KeyspaceParams;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.streaming.SessionInfo;
import org.apache.cassandra.streaming.StreamManager;
import org.apache.cassandra.streaming.StreamState;
import org.apache.cassandra.streaming.management.StreamStateCompositeData;
import org.apache.cassandra.transport.Server;
import org.apache.cassandra.transport.UnixSocketServer4x;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.JVMStabilityInspector;

public class CassandraAPI4x implements CassandraAPI
{
    private static final Logger logger = LoggerFactory.getLogger(CassandraAPI4x.class);

    private static final Supplier<SeedProvider> seedProvider = Suppliers.memoize(() -> new K8SeedProvider4x());

    @Override
    public void decommission(boolean force) throws InterruptedException
    {
        StorageService.instance.decommission(force);
    }

    @Override
    public Map<List<Long>, List<String>> checkConsistencyLevel(String consistencyLevelName, Integer rfPerDc)
    {
        try
        {
            IPartitioner partitioner = DatabaseDescriptor.getPartitioner();
            IEndpointSnitch endpointSnitch = DatabaseDescriptor.getEndpointSnitch();
            TokenMetadata tokenMetadata = StorageService.instance.getTokenMetadata().cloneOnlyTokenMap();

            ConsistencyLevel cl = ConsistencyLevel.valueOf(consistencyLevelName);

            Map<String, String> dcNames = new HashMap<>();

            for (InetAddressAndPort endpoint : tokenMetadata.getNormalAndBootstrappingTokenToEndpointMap().values())
            {
                String dc = endpointSnitch.getDatacenter(endpoint);
                assert dc != null;

                dcNames.put(dc, String.valueOf(rfPerDc));
            }

            Keyspace mockKs = Keyspace.mockKS(KeyspaceMetadata.create("none", KeyspaceParams.create(true,
                    ImmutableMap.<String, String>builder().put("class", "NetworkTopologyStrategy").putAll(dcNames).build())));

            AbstractReplicationStrategy mockStrategy = mockKs.getReplicationStrategy();
            mockStrategy.validateOptions();

            Collection<Range<Token>> tokenRanges = tokenMetadata.getPrimaryRangesFor(tokenMetadata.sortedTokens());

            Map<List<Long>, List<String>> results = new HashMap<>();

            // For each range check the endpoints can achieve cl using the midpoint
            for (Range<Token> range : tokenRanges)
            {
                Token midpoint = partitioner.midpoint(range.left, range.right);
                EndpointsForRange endpoints = mockStrategy.calculateNaturalReplicas(midpoint, tokenMetadata);

                if (!ReplicaPlans.isSufficientLiveReplicasForRead(mockKs, cl, endpoints))
                {
                    List<String> downEndpoints = new ArrayList<>();
                    for (InetAddressAndPort endpoint : endpoints.endpoints())
                    {
                        EndpointState epState = Gossiper.instance.getEndpointStateForEndpoint(endpoint);

                        if (!epState.isAlive())
                            downEndpoints.add(endpoint.toString());
                    }

                    int blockFor = cl.blockFor(mockKs);

                    if (downEndpoints.isEmpty() && endpoints.size() < blockFor)
                        downEndpoints.add(String.format("%d replicas required, but only %d nodes in the ring", blockFor, endpoints.size()));
                    else if (downEndpoints.isEmpty())
                        downEndpoints.add("Nodes Flapping");

                    results.put(ImmutableList.of((long) range.left.getTokenValue(), (long) range.right.getTokenValue()), downEndpoints);
                }
            }
            return results;
        }
        catch (Throwable e)
        {
            logger.error("Exception encountered", e);
            throw e;
        }
    }

    @Override
    public SeedProvider getK8SeedProvider()
    {
        return seedProvider.get();
    }

    public Set<InetAddress> reloadSeeds()
    {
        Field seedField = FBUtilities.getProtectedField(Gossiper.class, "seeds");

        Set<InetAddressAndPort> seeds = null;
        try
        {
            seeds = (Set<InetAddressAndPort>) seedField.get(Gossiper.instance);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }

        // Get the new set in the same that buildSeedsList does
        Set<InetAddressAndPort> tmp = new HashSet<>();
        try
        {
            for (InetAddressAndPort seed : getK8SeedProvider().getSeeds())
            {
                if (seed.equals(FBUtilities.getBroadcastAddressAndPort()))
                    continue;
                tmp.add(seed);
            }
        }
        // If using the SimpleSeedProvider invalid yaml added to the config since startup could
        // cause this to throw. Additionally, third party seed providers may throw exceptions.
        // Handle the error and return a null to indicate that there was a problem.
        catch (Throwable e)
        {
            JVMStabilityInspector.inspectThrowable(e);
            return null;
        }

        if (tmp.size() == 0)
        {
            return seeds.stream().map(s -> s.address).collect(Collectors.toSet());
        }

        if (tmp.equals(seeds))
        {
            return seeds.stream().map(s -> s.address).collect(Collectors.toSet());
        }

        // Add the new entries
        seeds.addAll(tmp);
        // Remove the old entries
        seeds.retainAll(tmp);
        logger.debug("New seed node list after reload {}", seeds);

        return seeds.stream().map(s -> s.address).collect(Collectors.toSet());
    }

    @Override
    public ChannelInitializer<Channel> makeSocketInitializer(Server.ConnectionTracker connectionTracker)
    {
        return UnixSocketServer4x.makeSocketInitializer(connectionTracker);
    }

    @Override
    public List<Map<String, String>> getEndpointStates()
    {
        List<Map<String,String>> result = new ArrayList<>();

        for (InetAddressAndPort endpoint : Gossiper.instance.getEndpoints())
        {
            EndpointState state = Gossiper.instance.getEndpointStateForEndpoint(endpoint);
            Map<String, String> states = new HashMap<>();
            for (Map.Entry<ApplicationState, VersionedValue> s : state.states())
            {
                states.put(s.getKey().name(), s.getValue().value);
            }

            states.put("ENDPOINT_IP", endpoint.address.getHostAddress());
            states.put("IS_ALIVE", Boolean.toString(state.isAlive()));
            result.add(states);
        }

        return result;
    }

    @Override
    public List<Map<String, List<Map<String, String>>>> getStreamInfo()
    {
        Set<StreamState> streams = StreamManager.instance.getCurrentStreams().stream()
                .map(StreamStateCompositeData::fromCompositeData)
                .collect(Collectors.toSet());

        List<Map<String, List<Map<String, String>>>> result = new ArrayList<>();

        for (StreamState status : streams)
        {
            Map<String, List<Map<String, String>>> streamInfo = new HashMap<>();
            List<Map<String, String>> sessionResults = new ArrayList<>();

            for (SessionInfo info : status.sessions)
            {
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
}
