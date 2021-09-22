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
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.shims.CassandraAPI;
import com.datastax.mgmtapi.shims.RpcStatementShim;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.reactivex.Single;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.concurrent.TPCTaskType;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.compaction.CompactionManager;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.gms.ApplicationState;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.gms.VersionedValue;
import org.apache.cassandra.hints.HintsService;
import org.apache.cassandra.locator.AbstractReplicationStrategy;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.locator.K8SeedProviderDse68;
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
import org.apache.cassandra.transport.UnixSocketServerDse68;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.JVMStabilityInspector;
import org.apache.cassandra.utils.flow.RxThreads;

public class DseAPI68 implements CassandraAPI
{
    private static final Logger logger = LoggerFactory.getLogger(DseAPI68.class);

    private static final Supplier<SeedProvider> seedProvider = Suppliers.memoize(K8SeedProviderDse68::new)::get;
    
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

            for (InetAddress endpoint : tokenMetadata.getNormalAndBootstrappingTokenToEndpointMap().values())
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
                List<InetAddress> endpoints = mockStrategy.calculateNaturalEndpoints(midpoint, tokenMetadata);

                if (!cl.isSufficientLiveNodes(mockKs, endpoints))
                {
                    List<String> downEndpoints = new ArrayList<>();
                    for (InetAddress endpoint : endpoints)
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

        Set<InetAddress> seeds = null;
        try
        {
            seeds = (Set<InetAddress>) seedField.get(Gossiper.instance);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }

        // Get the new set in the same that buildSeedsList does
        Set<InetAddress> tmp = new HashSet<>();
        try
        {
            for (InetAddress seed : getK8SeedProvider().getSeeds())
            {
                if (seed.equals(FBUtilities.getBroadcastAddress()))
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
            return new HashSet<>(seeds);
        }

        if (tmp.equals(seeds))
        {
            return new HashSet<>(seeds);
        }

        // Add the new entries
        seeds.addAll(tmp);
        // Remove the old entries
        seeds.retainAll(tmp);
        logger.debug("New seed node list after reload {}", seeds);

        return new HashSet<>(seeds);
    }

    @Override
    public ChannelInitializer<Channel> makeSocketInitializer(Server.ConnectionTracker connectionTracker)
    {
        return UnixSocketServerDse68.makeSocketInitializer(connectionTracker);
    }

    @Override
    public List<Map<String, String>> getEndpointStates()
    {
        List<Map<String, String>> result = new ArrayList<>();

        for (InetAddress endpoint : Gossiper.instance.getAllEndpoints())
        {
            EndpointState state = Gossiper.instance.getEndpointStateForEndpoint(endpoint);
            Map<String, String> states = new HashMap<>();
            for (Map.Entry<ApplicationState, VersionedValue> s : state.states())
            {
                states.put(s.getKey().name(), s.getValue().value);
            }

            states.put("ENDPOINT_IP", endpoint.getHostAddress());
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

    @Override
    public UntypedResultSet processQuery(String query, ConsistencyLevel consistencyLevel)
    {
        return QueryProcessor.processBlocking(query, consistencyLevel);
    }

    @Override
    public StorageService getStorageService()
    {
        return StorageService.instance;
    }

    @Override
    public IRoleManager getRoleManager()
    {
        return DatabaseDescriptor.getRoleManager();
    }

    @Override
    public CompactionManager getCompactionManager()
    {
        return CompactionManager.instance;
    }

    @Override
    public Gossiper getGossiper()
    {
        return Gossiper.instance;
    }

    @Override
    public Object handleRpcResult(Callable<Object> rpcResult)
    {
        return RxThreads.subscribeOnIo(Single.defer(() -> Single.fromCallable(rpcResult)), TPCTaskType.UNKNOWN);
    }

    @Override
    public String getLocalDataCenter()
    {
        return DatabaseDescriptor.getLocalDataCenter();
    }

    @Override
    public RpcStatementShim makeRpcStatement(String method, String[] params)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public HintsService getHintsService()
    {
        return HintsService.instance;
    }
}
