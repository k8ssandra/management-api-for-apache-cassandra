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

import javax.ws.rs.ApplicationPath;

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

public class NettyJaxrsIPCServer extends NettyJaxrsServer
{
    private final AtomicReference<IPCController> activeServerRef = new AtomicReference<>();

    private final EventLoopGroup eventLoopGroup;
    private final File socketFile;
    private Map<ChannelOption, Object> channelOptions = Collections.emptyMap();
    private List<ChannelHandler> httpChannelHandlers = Collections.emptyList();

    private int maxRequestSize = 1024 * 1024 * 10;
    private int maxInitialLineLength = 4096;
    private int maxHeaderSize = 8192;
    private int maxChunkSize = 8192;
    private int idleTimeout = 60;

    public NettyJaxrsIPCServer(EventLoopGroup eventLoopGroup, File socketFile)
    {
        this.eventLoopGroup = eventLoopGroup;
        this.socketFile = socketFile;
    }

    @Override
    public void setChannelOptions(final Map<ChannelOption, Object> channelOptions) {
        this.channelOptions = channelOptions == null ? Collections.<ChannelOption, Object>emptyMap() : channelOptions;
    }

    @Override
    public void setHttpChannelHandlers(final List<ChannelHandler> httpChannelHandlers) {
        this.httpChannelHandlers = httpChannelHandlers == null ? Collections.<ChannelHandler>emptyList() : httpChannelHandlers;
    }

    @Override
    public void start()
    {
        synchronized (activeServerRef)
        {
            IPCController activeServer = activeServerRef.get();
            if (activeServer != null && activeServer.isActive())
                return;

            if (activeServer != null)
            {
                activeServer.start();
            }
            else
            {
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
