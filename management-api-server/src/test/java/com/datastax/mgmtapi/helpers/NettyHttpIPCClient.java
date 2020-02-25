/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.helpers;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;

import com.google.common.util.concurrent.Uninterruptibles;

import com.datastax.mgmtapi.ipc.IPCController;
import com.datastax.mgmtapi.Cli;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.CharsetUtil;

import static org.junit.Assert.fail;

/**
 * Simple async http client over unix sockets (only run 1 request at a time)
 */
public class NettyHttpIPCClient
{
    private IPCController client;

    private final AtomicReference<CompletableFuture<FullHttpResponse>> activeRequestFuture = new AtomicReference<>();

    public NettyHttpIPCClient(String socketFileStr) throws SSLException
    {
        this(socketFileStr, null, null, null);
    }

    public NettyHttpIPCClient(String endpointStr, File clientCaFile, File clientCertFile, File clientKeyFile) throws SSLException
    {
        File socketFile = new File(endpointStr);

        SslContext sslContext = null;

        if (clientCaFile != null && clientCertFile != null && clientKeyFile != null)
        {
            sslContext = SslContextBuilder.forClient()
                        .trustManager(clientCaFile)
                        .keyManager(clientCertFile, clientKeyFile, null)
                        .protocols(Cli.PROTOCOL_TLS_V1_2)
                        .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                        .sessionCacheSize(0)
                        .sessionTimeout(0).build();

        }

        final SslContext finalSslContext = sslContext;

        client = IPCController.newClient()
                .withEventLoop(eventLoop())
                .withSocketFile(socketFile)
                .withChannelHandler(new ChannelInitializer<Channel>()
                {
                    @Override
                    protected void initChannel(Channel ch) throws Exception
                    {

                        if (finalSslContext != null)
                            ch.pipeline().addFirst(finalSslContext.newHandler(ch.alloc()));

                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(1 << 20));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>()
                        {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg)
                            {
                                CompletableFuture<FullHttpResponse> f = activeRequestFuture.getAndUpdate(c -> null);
                                if (f == null)
                                    throw new RuntimeException("Missing callback");

                                f.complete(msg);
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
                            {
                                CompletableFuture<FullHttpResponse> f = activeRequestFuture.getAndUpdate(c -> null);
                                if (f == null)
                                    throw new RuntimeException("Missing callback");

                                f.completeExceptionally(cause);
                            }
                        });
                    }
                })
                .build();

        int tries = 0;
        while (tries++ < 5)
        {
            Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);

            try
            {
                client.start();
                return;
            }
            catch (Throwable t)
            {
            }
        }

        fail("Unable to connect to management api");
    }


    public CompletableFuture<FullHttpResponse> get(URL url)
    {
        CompletableFuture<FullHttpResponse> result = new CompletableFuture<>();

        if (!activeRequestFuture.compareAndSet(null, result))
            throw new RuntimeException("outstanding request");

        // Prepare the HTTP request.
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url.getFile());
        request.headers().set(HttpHeaderNames.HOST, url.getHost());

        Channel channel = client.channel().orElseThrow(() -> new RuntimeException("NoClient"));
        // Send the HTTP request.
        channel.writeAndFlush(request);

        return result;
    }

    public CompletableFuture<FullHttpResponse> post(URL url, final CharSequence body) throws UnsupportedEncodingException
    {
        return post(url, body, "application/json");
    }

    public CompletableFuture<FullHttpResponse> post(URL url, final CharSequence body, String contentType) throws UnsupportedEncodingException
    {

        CompletableFuture<FullHttpResponse> result = new CompletableFuture<>();

        if (!activeRequestFuture.compareAndSet(null, result))
            throw new RuntimeException("outstanding request");

        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, url.getFile());
        request.headers().set(HttpHeaders.Names.CONTENT_TYPE, contentType);
        request.headers().set(HttpHeaderNames.HOST, url.getHost());

        if (body != null)
        {
            request.content().writeBytes(body.toString().getBytes(CharsetUtil.UTF_8.name()));
            request.headers().set(HttpHeaders.Names.CONTENT_LENGTH, request.content().readableBytes());
        }
        else
        {
            request.headers().set(HttpHeaders.Names.CONTENT_LENGTH, 0);
        }

        Channel channel = client.channel().orElseThrow(() -> new RuntimeException("NoClient"));
        // Send the HTTP request.
        channel.writeAndFlush(request);

        return result;
    }

    private EventLoopGroup eventLoop()
    {
        if (Epoll.isAvailable())
        {
            Epoll.ensureAvailability();
            return new EpollEventLoopGroup(1);
        }

        if (KQueue.isAvailable())
        {
            KQueue.ensureAvailability();
            return new KQueueEventLoopGroup(1);
        }

        throw new RuntimeException();
    }
}
