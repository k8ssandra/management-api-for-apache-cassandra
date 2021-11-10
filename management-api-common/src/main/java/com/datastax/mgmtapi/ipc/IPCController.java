/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.ipc;

import java.io.File;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;

/**
 * Responsible for communication between two processes over named unix sockets
 */
public class IPCController
{
    private static final Logger logger = LoggerFactory.getLogger(IPCController.class);

    private final AbstractBootstrap bootstrap;

    private final boolean isClient;
    private final File socketFile;
    private final AtomicReference<Channel> activeChannelRef;

    private IPCController(EventLoopGroup eventLoopGroup, Map<ChannelOption, Object> channelOptions,
            ChannelInitializer<Channel> channelPipeline, File socketFile, boolean isClient)
    {
        this.isClient = isClient;
        this.socketFile = socketFile;
        this.bootstrap = createPipeline(eventLoopGroup, channelOptions, channelPipeline, isClient);
        this.activeChannelRef = new AtomicReference<>();
    }

    private AbstractBootstrap createPipeline(EventLoopGroup eventLoopGroup,
            Map<ChannelOption, Object> channelOptions,
            ChannelInitializer<Channel> channelPipeline, boolean isClient)
    {
        if (Epoll.isAvailable() && !(eventLoopGroup instanceof EpollEventLoopGroup))
        {
            throw new IllegalArgumentException("eventLoopGroup must be epoll based under Linux");
        }

        AbstractBootstrap b = isClient ? new Bootstrap() : new ServerBootstrap();
        b.group(eventLoopGroup);

        if (channelOptions != null)
        {
            for (Map.Entry<ChannelOption, Object> option : channelOptions.entrySet())
            {
                b = b.option(option.getKey(), option.getValue());

                if (!isClient)
                    b = ((ServerBootstrap)b).childOption(option.getKey(), option.getValue());
            }
        }

        if (isClient)
        {
            if (Epoll.isAvailable())
            {
                b = b.channel(EpollDomainSocketChannel.class);
            }
            else if (KQueue.isAvailable())
            {
                b = b.channel(KQueueDomainSocketChannel.class);
            }
            else
            {
                throw new RuntimeException("Neither epoll nor kqueue was found. Unable to initialize channel pipeline");
            }
        }
        else
        {
            if (Epoll.isAvailable())
            {
                b = b.channel(EpollServerDomainSocketChannel.class);
            }
            else if (KQueue.isAvailable())
            {
                b = b.channel(KQueueServerDomainSocketChannel.class);
            }
            else
            {
                throw new RuntimeException("Neither epoll nor kqueue was found. Unable to initialize server channel pipeline");
            }
        }

        return isClient ? b.handler(channelPipeline) : ((ServerBootstrap)b).childHandler(channelPipeline);
    }

    public boolean isActive()
    {
        Channel activeChannel = activeChannelRef.get();
        return activeChannel != null && activeChannel.isActive();
    }

    public Optional<Channel> channel()
    {
        return Optional.ofNullable(activeChannelRef.get());
    }

    public void start()
    {
        //Avoid setting started to true until our setup is done
        synchronized (activeChannelRef)
        {
            if (activeChannelRef.get() == null)
            {
                logger.info("Starting {}", isClient ? "Client" : "Server");

                SocketAddress s = new DomainSocketAddress(socketFile);
                Channel activeChannel = isClient ?
                                        ((Bootstrap)bootstrap).connect(s).syncUninterruptibly().channel() :
                                        bootstrap.bind(s).syncUninterruptibly().channel();

                boolean b = activeChannelRef.compareAndSet(null, activeChannel);
                assert b : "Active channel already set";
                logger.info("Started {}", isClient ? "Client" : "Server");
            }
        }
    }

    public void stop()
    {
        //Avoid setting started to true until our setup is done
        synchronized (activeChannelRef)
        {
            Channel activeChannel = activeChannelRef.get();
            if (activeChannel != null)
            {
                if (activeChannel.isActive())
                {
                    activeChannel.close().syncUninterruptibly();
                }
                else
                {
                    logger.info("Channel no longer active");
                }
                boolean b = activeChannelRef.compareAndSet(activeChannel, null);
                assert b : "Active channel already removed";
                logger.info("Stopped {}", isClient ? "Client" : "Server");
            }
        }
    }

    public static IPCBuilder newClient()
    {
        return new IPCBuilder(true);
    }

    public static IPCBuilder newServer()
    {
        return new IPCBuilder(false);
    }

    public static final class IPCBuilder
    {
        private File socketFile;
        private EventLoopGroup eventLoopGroup;
        private Map<ChannelOption, Object> channelOptions;
        private ChannelInitializer<Channel> channelHandler;

        private final boolean isClient;

        private IPCBuilder(boolean isClient)
        {
            this.isClient = isClient;
        }

        public IPCBuilder withSocketFile(File socketFile)
        {
            this.socketFile = socketFile;
            return this;
        }

        public IPCBuilder withEventLoop(EventLoopGroup eventLoopGroup)
        {
            this.eventLoopGroup = eventLoopGroup;
            return this;
        }

        public IPCBuilder withChannelOptions(Map<ChannelOption, Object> channelOptions)
        {
            this.channelOptions = channelOptions;
            return this;
        }

        public IPCBuilder withChannelHandler(ChannelInitializer<Channel> channelHandler)
        {
            this.channelHandler = channelHandler;
            return this;
        }

        public IPCController build()
        {
            if (socketFile == null)
                throw new IllegalArgumentException("socketFile is required");

            if (eventLoopGroup == null)
                throw new IllegalArgumentException("eventLoopGroup is required");

            if (channelHandler == null)
                throw new IllegalArgumentException("channelHandler required");

            return new IPCController(eventLoopGroup, channelOptions, channelHandler, socketFile, isClient);
        }
    }
}
