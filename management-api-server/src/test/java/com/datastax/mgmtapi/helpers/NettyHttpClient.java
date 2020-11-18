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

import com.datastax.mgmtapi.Cli;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
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
public class NettyHttpClient
{
    private Channel client;

    private final AtomicReference<CompletableFuture<FullHttpResponse>> activeRequestFuture = new AtomicReference<>();

    public NettyHttpClient(URL endpoint) throws SSLException
    {
        this(endpoint, null, null, null);
    }

    public NettyHttpClient(URL endpoint, File clientCaFile, File clientCertFile, File clientKeyFile) throws SSLException
    {
        SslContext sslContext = null;

        if (clientCaFile != null && clientCertFile != null && clientKeyFile != null)
        {
            sslContext = SslContextBuilder.forClient()
                    .trustManager(clientCaFile)
                    .keyManager(clientCertFile, clientKeyFile, null)
                    .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                    .protocols(Cli.PROTOCOL_TLS_V1_2)
                    .sessionCacheSize(0)
                    .sessionTimeout(0).build();

        }

        final SslContext finalSslContext = sslContext;
        EventLoopGroup group = new NioEventLoopGroup();
        try
        {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>()
                    {
                        @Override
                        protected void initChannel(Channel ch) throws Exception
                        {

                            if (finalSslContext != null)
                                ch.pipeline().addFirst(finalSslContext.newHandler(ch.alloc()));

                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(1<<20));
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
                    });

            int tries = 0;
            while (tries++ < 5)
            {
                Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);

                try
                {
                    client = b.connect(endpoint.getHost(), endpoint.getPort()).syncUninterruptibly().channel();
                    return;
                }
                catch (Throwable t)
                {
                }
            }
        }
        finally
        {

        }

        fail("Unable to connect to management api");
    }


    public CompletableFuture<FullHttpResponse> get(URL url)
    {
        return buildAndSendRequest(HttpMethod.GET, url);
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

        // Send the HTTP request.
        client.writeAndFlush(request);

        return result;
    }

    public CompletableFuture<FullHttpResponse> delete(URL url)
    {
        return buildAndSendRequest(HttpMethod.DELETE, url);
    }

    /**
     * Common method for building and sending GET or DELETE requests.
     * @param method Request method (either HttpMethod.GET or HttpMethod.DELETE)
     * @param url URL to send the request
     */
    private CompletableFuture<FullHttpResponse> buildAndSendRequest(HttpMethod method, URL url)
    {
        CompletableFuture<FullHttpResponse> result = new CompletableFuture<>();

        if (!activeRequestFuture.compareAndSet(null, result))
            throw new RuntimeException("outstanding request");

        // Prepare the HTTP request.
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, url.getFile());
        request.headers().set(HttpHeaderNames.HOST, url.getHost());

        // Send the HTTP request.
        client.writeAndFlush(request);

        return result;
    }
}
