/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;


import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.datastax.mgmtapi.ipc.IPCController;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.plugins.server.netty.RequestDispatcher;
import org.jboss.resteasy.plugins.server.netty.RequestHandler;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpRequestDecoder;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpResponseEncoder;
import org.jboss.resteasy.util.EmbeddedServerHelper;

public class NettyJaxrsIPCServer extends NettyJaxrsServer
{
    private final AtomicReference<IPCController> activeServerRef = new AtomicReference<>();

    private final EventLoopGroup eventLoopGroup;
    private final File socketFile;
    private Map<ChannelOption, Object> channelOptions = Collections.emptyMap();
    private List<ChannelHandler> httpChannelHandlers = Collections.emptyList();

    private final int maxRequestSize = 1024 * 1024 * 10;
    private final int maxInitialLineLength = 4096;
    private final int maxHeaderSize = 8192;
    private final int maxChunkSize = 8192;
    private final int idleTimeout = 60;
    // From the internals of Resteasy
    private final EmbeddedServerHelper serverHelper = new EmbeddedServerHelper();

    public NettyJaxrsIPCServer(EventLoopGroup eventLoopGroup, File socketFile)
    {
        this.eventLoopGroup = eventLoopGroup;
        this.socketFile = socketFile;
    }

    @Override
    public NettyJaxrsServer setChannelOptions(final Map<ChannelOption, Object> channelOptions) {
        this.channelOptions = channelOptions == null ? Collections.<ChannelOption, Object>emptyMap() : channelOptions;
        return this;
    }

    @Override
    public NettyJaxrsServer setHttpChannelHandlers(final List<ChannelHandler> httpChannelHandlers) {
        this.httpChannelHandlers = httpChannelHandlers == null ? Collections.<ChannelHandler>emptyList() : httpChannelHandlers;
        return this;
    }

    @Override
    public NettyJaxrsServer start()
    {
        synchronized (activeServerRef)
        {
            IPCController activeServer = activeServerRef.get();
            if (activeServer != null && activeServer.isActive())
                return this;

            if (activeServer != null)
            {
                activeServer.start();
            }
            else
            {
                serverHelper.checkDeployment(deployment);

                // dynamically set the root path (the user can rewrite it by calling setRootResourcePath)
                String appPath = serverHelper.checkAppDeployment(deployment);
                if (appPath != null && (root == null || "".equals(root))) {
                  setRootResourcePath(appPath);
                }

                activeServer = IPCController.newServer()
                        .withEventLoop(eventLoopGroup)
                        .withSocketFile(socketFile)
                        .withChannelOptions(channelOptions)
                        .withChannelHandler(new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(Channel ch) throws Exception
                            {
                                setupHandlers(ch, createRequestDispatcher(), RestEasyHttpRequestDecoder.Protocol.HTTP);
                            }
                        }).build();

                activeServer.start();
                boolean b = activeServerRef.compareAndSet(null, activeServer);
                assert b : "Already active";
            }
        }
        return this;
    }

    @Override
    public void stop()
    {
         synchronized (activeServerRef)
         {
             IPCController activeServer = activeServerRef.get();
             if (activeServer != null)
                 activeServer.stop();
         }
    }

    protected void setupHandlers(Channel ch, RequestDispatcher dispatcher, RestEasyHttpRequestDecoder.Protocol protocol) {
        ChannelPipeline channelPipeline = ch.pipeline();

        channelPipeline.addLast(new HttpRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize));
        channelPipeline.addLast(new HttpResponseEncoder());
        channelPipeline.addLast(new HttpObjectAggregator(maxRequestSize));
        channelPipeline.addLast(httpChannelHandlers.toArray(new ChannelHandler[httpChannelHandlers.size()]));
        channelPipeline.addLast(new RestEasyHttpRequestDecoder(dispatcher.getDispatcher(), root, protocol));
        channelPipeline.addLast(new RestEasyHttpResponseEncoder());

        if (idleTimeout > 0)
        {
            channelPipeline.addLast("idleStateHandler", new IdleStateHandler(0, 0, idleTimeout));
        }

        channelPipeline.addLast(new RequestHandler(dispatcher));
    }

}
