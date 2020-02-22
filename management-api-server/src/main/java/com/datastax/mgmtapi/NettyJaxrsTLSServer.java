package com.datastax.mgmtapi;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import javax.ws.rs.ApplicationPath;

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

public class NettyJaxrsTLSServer extends NettyJaxrsServer
{
    private final SslContext sslContext;
    private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(8);
    private Map<ChannelOption, Object> channelOptions = Collections.emptyMap();
    private int maxRequestSize = 1024 * 1024 * 10;
    private int maxInitialLineLength = 4096;
    private int maxHeaderSize = 8192;
    private int maxChunkSize = 8192;
    private int idleTimeout = 60;

    public NettyJaxrsTLSServer(SslContext sslContext)
    {
        this.sslContext = sslContext;
    }

    public void start() {
        deployment.start();
        // dynamically set the root path (the user can rewrite it by calling setRootResourcePath)
        if (deployment.getApplication() != null) {
            ApplicationPath appPath = deployment.getApplication().getClass().getAnnotation(ApplicationPath.class);
            if (appPath != null && (root == null || "".equals(root))) {
                // annotation is present and original root is not set
                String path = appPath.value();
                setRootResourcePath(path);
            }
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
