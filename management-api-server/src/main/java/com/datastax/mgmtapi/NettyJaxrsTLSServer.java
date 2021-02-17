/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.plugins.server.netty.RequestDispatcher;
import org.jboss.resteasy.plugins.server.netty.RequestHandler;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpRequestDecoder;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpResponseEncoder;
import org.jboss.resteasy.util.EmbeddedServerHelper;

public class NettyJaxrsTLSServer extends NettyJaxrsServer
{
    private final SslContext sslContext;
    private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);
    private final Map<ChannelOption, Object> channelOptions = Collections.emptyMap();
    private final int maxRequestSize = 1024 * 1024 * 10;
    private final int maxInitialLineLength = 4096;
    private final int maxHeaderSize = 8192;
    private final int maxChunkSize = 8192;
    private final int idleTimeout = 60;
    // From the internals of Resteasy
    private final EmbeddedServerHelper serverHelper = new EmbeddedServerHelper();

    public NettyJaxrsTLSServer(SslContext sslContext)
    {
        this.sslContext = sslContext;
    }

    @Override
    public NettyJaxrsServer start() {
        serverHelper.checkDeployment(deployment);
        // dynamically set the root path (the user can rewrite it by calling setRootResourcePath)
        String appPath = serverHelper.checkAppDeployment(deployment);
        if (appPath != null && (root == null || "".equals(root))) {
            setRootResourcePath(appPath);
        }

        // Configure the server.
        bootstrap.group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>()
                {
                    @Override
                    protected void initChannel(Channel ch) throws Exception
                    {
                        setupHandlers(ch, createRequestDispatcher(), RestEasyHttpRequestDecoder.Protocol.HTTPS);
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 100)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        for (Map.Entry<ChannelOption, Object> entry : channelOptions.entrySet()) {
            bootstrap.option(entry.getKey(), entry.getValue());
        }

        final InetSocketAddress socketAddress;
        if (null == hostname || hostname.isEmpty()) {
            socketAddress = new InetSocketAddress(configuredPort);
        } else {
            socketAddress = new InetSocketAddress(hostname, configuredPort);
        }

        Channel channel = bootstrap.bind(socketAddress).syncUninterruptibly().channel();
        runtimePort = ((InetSocketAddress) channel.localAddress()).getPort();
        return this;
    }

    protected void setupHandlers(Channel ch, RequestDispatcher dispatcher, RestEasyHttpRequestDecoder.Protocol protocol) {
        ChannelPipeline channelPipeline = ch.pipeline();

        channelPipeline.addFirst(sslContext.newHandler(ch.alloc()));

        channelPipeline.addLast(new HttpRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize));
        channelPipeline.addLast(new HttpResponseEncoder());
        channelPipeline.addLast(new HttpObjectAggregator(maxRequestSize));
        channelPipeline.addLast(new RestEasyHttpRequestDecoder(dispatcher.getDispatcher(), root, protocol));
        channelPipeline.addLast(new RestEasyHttpResponseEncoder());

        if (idleTimeout > 0)
        {
            channelPipeline.addLast("idleStateHandler", new IdleStateHandler(0, 0, idleTimeout));
        }

        channelPipeline.addLast(new RequestHandler(dispatcher));
    }


    @Override
    public void stop()
    {
        runtimePort = -1;
        eventLoopGroup.shutdownGracefully();
    }
}
