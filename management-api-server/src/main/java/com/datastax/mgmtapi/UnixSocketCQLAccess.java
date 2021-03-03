/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.File;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.google.common.collect.Maps;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.connection.ReconnectionPolicy;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.loadbalancing.LoadBalancingPolicy;
import com.datastax.oss.driver.api.core.loadbalancing.NodeDistance;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.NodeStateListener;
import com.datastax.oss.driver.api.core.metadata.schema.SchemaChangeListener;
import com.datastax.oss.driver.api.core.session.Request;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.api.core.tracker.RequestTracker;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.internal.core.context.DefaultDriverContext;
import com.datastax.oss.driver.internal.core.context.DefaultNettyOptions;
import com.datastax.oss.driver.internal.core.context.NettyOptions;
import com.datastax.oss.driver.internal.core.cql.CqlPrepareAsyncProcessor;
import com.datastax.oss.driver.internal.core.cql.CqlPrepareSyncProcessor;
import com.datastax.oss.driver.internal.core.cql.CqlRequestAsyncProcessor;
import com.datastax.oss.driver.internal.core.cql.CqlRequestSyncProcessor;
import com.datastax.oss.driver.internal.core.metadata.DefaultTopologyMonitor;
import com.datastax.oss.driver.internal.core.metadata.NodeInfo;
import com.datastax.oss.driver.internal.core.metadata.TopologyMonitor;
import com.datastax.oss.driver.internal.core.session.RequestProcessorRegistry;
import com.datastax.oss.driver.internal.core.util.collection.QueryPlan;
import com.datastax.oss.driver.internal.core.util.collection.SimpleQueryPlan;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.PromiseCombiner;

import static java.time.temporal.ChronoUnit.SECONDS;

public class UnixSocketCQLAccess
{
    private static final Logger logger = LoggerFactory.getLogger(UnixSocketCQLAccess.class);
    private static final ConcurrentMap<File, UnixSocketCQLAccess> cache = Maps.newConcurrentMap();
    private final CqlSession session;
    private final EndPoint unixSocketEndpoint;

    public static Optional<CqlSession> get(File unixSocket)
    {
        while (true)
        {
            UnixSocketCQLAccess client = cache.get(unixSocket);

            if (client != null && !client.session.isClosed())
                return Optional.of(client.session);

            //might throw exceptions if unix sock does not exist, so we might have nothing to return
            try
            {
                UnixSocketCQLAccess newClient = new UnixSocketCQLAccess(unixSocket);
                if (client == null && cache.putIfAbsent(unixSocket, newClient) == null)
                    return Optional.of(newClient.session);

                if (client != null && cache.replace(unixSocket, client, newClient))
                    return Optional.of(newClient.session);

                //Someone else opened a session, so close and try again
                newClient.session.close();
            }
            catch (AllNodesFailedException e)
            {
                return Optional.empty();
            }
        }
    }

    private UnixSocketCQLAccess(File unixSocket)
    {
        unixSocketEndpoint = new EndPoint()
        {
            @Override
            public SocketAddress resolve()
            {
                return new DomainSocketAddress(unixSocket);
            }

            @Override
            public String asMetricPrefix()
            {
                return unixSocket.toString();
            }

            @Override
            public String toString()
            {
                return unixSocket.toString();
            }
        };

        session = new LocalSessionBuilder(unixSocketEndpoint)
                .withConfigLoader(DriverConfigLoader.programmaticBuilder()
                        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.of(30, SECONDS))
                        // force protocol V4 for now
                        .withString(DefaultDriverOption.PROTOCOL_VERSION, "V4")
                        .build())
                .build();
    }

    static class LocalSessionBuilder extends CqlSessionBuilder
    {
        final EndPoint unixSocketEndpoint;

        LocalSessionBuilder(EndPoint unixSocketEndpoint)
        {
            super();
            this.unixSocketEndpoint = unixSocketEndpoint;
            this.addContactEndPoint(unixSocketEndpoint);
        }

        @Override
        protected DriverContext buildContext(DriverConfigLoader configLoader, List<TypeCodec<?>> typeCodecs,
                NodeStateListener nodeStateListener, SchemaChangeListener schemaChangeListener, RequestTracker requestTracker,
                Map<String, String> localDatacenters, Map<String, Predicate<Node>> nodeFilters, ClassLoader classLoader)
        {
            return new LocalDriverContext(unixSocketEndpoint, configLoader, typeCodecs, nodeStateListener, schemaChangeListener,
                    requestTracker, localDatacenters, nodeFilters, classLoader);
        }
    }

    static class LocalDriverContext extends DefaultDriverContext
    {
        final EndPoint unixSocketEndpoint;
        volatile Node unixSocketNode = null;

        LocalDriverContext(EndPoint unixSocketEndpoint, DriverConfigLoader configLoader, List<TypeCodec<?>> typeCodecs,
                NodeStateListener nodeStateListener, SchemaChangeListener schemaChangeListener, RequestTracker requestTracker,
                Map<String, String> localDatacenters, Map<String, Predicate<Node>> nodeFilters, ClassLoader classLoader)
        {
            super(configLoader, typeCodecs, nodeStateListener, schemaChangeListener, requestTracker, localDatacenters, nodeFilters,
                    classLoader);

            this.unixSocketEndpoint = unixSocketEndpoint;
        }

        @Override
        protected TopologyMonitor buildTopologyMonitor()
        {
            return new DefaultTopologyMonitor(this)
            {
                // This local node can have multiple endpoints (unix-sock, tcp-ip) for the same UUID
                // The driver can't handle this so we filter the tcp-ip one out.
                // Note only the local unix-socket connection can see these dups
                @Override
                public CompletionStage<Iterable<NodeInfo>> refreshNodeList()
                {
                    return super.refreshNodeList()
                            .thenApply(nodeInfos -> {
                                Map<UUID, NodeInfo> filteredNodeInfo = new LinkedHashMap<>();
                                for (NodeInfo nodeInfo : nodeInfos)
                                {
                                    NodeInfo dupNode = filteredNodeInfo.get(nodeInfo.getHostId());

                                    if (dupNode != null)
                                    {
                                        //Keep the unix socket one, otherwise overwrite the tcp-ip one.
                                        if (dupNode.getEndPoint().resolve().equals(unixSocketEndpoint.resolve()))
                                            continue;

                                        // This means there's an actual dup UUID!
                                        if (!nodeInfo.getEndPoint().resolve().equals(unixSocketEndpoint.resolve()))
                                            throw new IllegalArgumentException(String.format("Multiple entries with same key: %s and %s", dupNode, nodeInfo));
                                    }

                                    filteredNodeInfo.put(nodeInfo.getHostId(), nodeInfo);
                                }

                                return filteredNodeInfo.values();
                            });
                }
            };
        }

        @Override
        protected NettyOptions buildNettyOptions()
        {
            final EventLoopGroup eventLoopGroup = eventLoop();

            return new DefaultNettyOptions(this) {

                @Override
                public Class<? extends Channel> channelClass()
                {
                    return Epoll.isAvailable() ? EpollDomainSocketChannel.class : KQueueDomainSocketChannel.class;
                }

                @Override
                public EventLoopGroup ioEventLoopGroup()
                {
                    return eventLoopGroup;
                }

                @Override
                public EventExecutorGroup adminEventExecutorGroup()
                {
                    return eventLoopGroup;
                }


                @Override
                public Future<Void> onClose() {
                    DefaultPromise<Void> closeFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
                    GlobalEventExecutor.INSTANCE.execute(
                            () -> {
                                DriverExecutionProfile config = getConfig().getDefaultProfile();
                                PromiseCombiner combiner = new PromiseCombiner();
                                combiner.add(
                                        adminEventExecutorGroup().shutdownGracefully(
                                                config.getInt(DefaultDriverOption.NETTY_IO_SHUTDOWN_QUIET_PERIOD),
                                                config.getInt(DefaultDriverOption.NETTY_IO_SHUTDOWN_TIMEOUT),
                                                TimeUnit.valueOf(config.getString(DefaultDriverOption.NETTY_IO_SHUTDOWN_UNIT))));

                                combiner.finish(closeFuture);
                            });
                    closeFuture.addListener(f -> getTimer().stop());
                    return closeFuture;
                }
            };
        }

        @Override
        protected ReconnectionPolicy buildReconnectionPolicy()
        {
            ReconnectionPolicy.ReconnectionSchedule noBackoff = () -> Duration.ofSeconds(10);

            return new ReconnectionPolicy() {
                @Override
                public ReconnectionSchedule newNodeSchedule(Node node)
                {
                    return noBackoff;
                }

                @Override
                public ReconnectionSchedule newControlConnectionSchedule(boolean isInitialConnection)
                {
                    return noBackoff;
                }

                @Override
                public void close()
                {

                }
            };
        }

        @Override
        protected Map<String, LoadBalancingPolicy> buildLoadBalancingPolicies()
        {
            return Collections.singletonMap(DriverExecutionProfile.DEFAULT_NAME, new LoadBalancingPolicy() {

                DistanceReporter distanceReporter;

                @Override
                public void init(Map<UUID, Node> nodes, DistanceReporter distanceReporter)
                {
                    this.distanceReporter = distanceReporter;

                    for (Map.Entry<UUID, Node> e : nodes.entrySet())
                    {
                        Node n = e.getValue();
                        if (n.getEndPoint().resolve().equals(unixSocketEndpoint.resolve()))
                        {
                            unixSocketNode = n;
                            distanceReporter.setDistance(n, NodeDistance.LOCAL);
                        }
                    }
                }

                @Override
                public Queue<Node> newQueryPlan(Request request, Session session)
                {
                    final Node uxNode = unixSocketNode;
                    return uxNode == null ? QueryPlan.EMPTY : new SimpleQueryPlan(uxNode);
                }

                /**
                 * The key for all these is to always pass the defaultNode instance in since the
                 * query plan will always return this.
                 */
                @Override
                public void onAdd(Node node)
                {
                    if (node.getEndPoint().resolve().equals(unixSocketEndpoint.resolve()))
                    {
                        unixSocketNode = node;
                        distanceReporter.setDistance(node, NodeDistance.LOCAL);
                    }
                }

                @Override
                public void onUp(Node node)
                {
                    if (node.getEndPoint().resolve().equals(unixSocketEndpoint.resolve()))
                    {
                        unixSocketNode = node;
                        distanceReporter.setDistance(node, NodeDistance.LOCAL);
                    }
                }

                @Override
                public void onDown(Node node)
                {
                    if (node.getEndPoint().resolve().equals(unixSocketEndpoint.resolve()))
                    {
                        unixSocketNode = null;
                        distanceReporter.setDistance(node, NodeDistance.IGNORED);
                    }
                }

                @Override
                public void onRemove(Node node)
                {
                    if (node.getEndPoint().resolve().equals(unixSocketEndpoint.resolve()))
                    {
                        unixSocketNode = null;
                        distanceReporter.setDistance(node, NodeDistance.IGNORED);
                    }
                }

                @Override
                public void close()
                {

                }
            });
        }

        @Override
        protected RequestProcessorRegistry buildRequestProcessorRegistry() {
            String logPrefix = getSessionName();

            // regular requests (sync and async)
            CqlRequestAsyncProcessor cqlRequestAsyncProcessor = new CqlRequestAsyncProcessor();
            CqlRequestSyncProcessor cqlRequestSyncProcessor =
                    new CqlRequestSyncProcessor(cqlRequestAsyncProcessor);

            // prepare requests (sync and async)
            CqlPrepareAsyncProcessor cqlPrepareAsyncProcessor = new CqlPrepareAsyncProcessor();
            CqlPrepareSyncProcessor cqlPrepareSyncProcessor =
                    new CqlPrepareSyncProcessor(cqlPrepareAsyncProcessor);


            return new RequestProcessorRegistry(
                    logPrefix,
                    cqlRequestSyncProcessor,
                    cqlRequestAsyncProcessor,
                    cqlPrepareSyncProcessor,
                    cqlPrepareAsyncProcessor);
        }
    }

    private static EventLoopGroup eventLoop()
    {
        if (Epoll.isAvailable())
        {
            Epoll.ensureAvailability();
            return new EpollEventLoopGroup(2);
        }

        if (KQueue.isAvailable())
        {
            KQueue.ensureAvailability();
            return new KQueueEventLoopGroup(2);
        }

        throw new RuntimeException();
    }
}
