/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.junit.Assert;
import org.junit.Test;

import com.datastax.mgmtapi.ipc.IPCController;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.test.TestPortProvider;

import static org.jboss.resteasy.test.TestPortProvider.generateURL;

public class NettyHttpOverIPCTest
{
    static String BASE_URI = generateURL("");

    static final int IDLE_TIMEOUT = 10;

    @Test(timeout= IDLE_TIMEOUT * 1000 + 10000)
    public void testIdleCloseConnectionNonIPC() throws Exception
    {
        NettyJaxrsServer netty = new NettyJaxrsServer();
        ResteasyDeployment deployment = new ResteasyDeploymentImpl();
        netty.setDeployment(deployment);
        netty.setPort(TestPortProvider.getPort());
        netty.setRootResourcePath("");
        netty.setSecurityDomain(null);
        netty.setIdleTimeout(IDLE_TIMEOUT);
        netty.start();
        deployment.getRegistry().addSingletonResource(new Resource());
        callAndIdle();
        netty.stop();
    }

    private void callAndIdle() throws InterruptedException, MalformedURLException
    {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(4096));
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                                    System.out.println("HTTP response from resteasy: "+msg);
                                    Assert.assertEquals(HttpResponseStatus.OK, msg.status());
                                }
                            });
                        }
                    });

            // first request;
            URL url = new URL(BASE_URI+"/test");
            // Make the connection attempt.
            final Channel ch = b.connect(url.getHost(), url.getPort()).sync().channel();

            // Prepare the HTTP request.
            HttpRequest request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1, HttpMethod.GET, url.getFile());
            request.headers().set(HttpHeaderNames.HOST, url.getHost());
            request.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            // Send the HTTP request.
            ch.writeAndFlush(request);

            // waiting for server close connection after idle.
            ch.closeFuture().await();
        } finally {
            // Shut down executor threads to exit.
            group.shutdownGracefully();
        }
    }

    @Test
    public void testHttpIPC() throws IOException, InterruptedException
    {
        if (!shouldRun())
            return;

        File socketFile = Files.createTempFile("http-over-ipc-test-", ".sock").toFile();
        socketFile.delete();
        EventLoopGroup loopGroup = eventLoop();

        ResteasyDeployment deployment = new ResteasyDeploymentImpl();
        NettyJaxrsIPCServer server = new NettyJaxrsIPCServer(loopGroup, socketFile);
        server.setDeployment(deployment);
        server.setRootResourcePath("");
        server.setIdleTimeout(IDLE_TIMEOUT);
        server.setSecurityDomain(null);

        server.start();

        deployment.getRegistry().addSingletonResource(new Resource());

        IPCController client = null;
        CountDownLatch latch = new CountDownLatch(1);
        try
        {
            client = IPCController.newClient()
                    .withEventLoop(eventLoop())
                    .withSocketFile(socketFile)
                    .withChannelHandler(new ChannelInitializer<Channel>()
                    {
                        @Override
                        protected void initChannel(Channel ch) throws Exception
                        {
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(4096));
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>()
                            {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg)
                                {
                                    System.out.println("HTTP response from resteasy: "+ msg.content().getCharSequence(0, msg.content().readableBytes(), Charset.defaultCharset()));
                                    Assert.assertEquals(HttpResponseStatus.OK, msg.status());
                                    latch.countDown();
                                }
                            });
                        }
                    })
                    .build();

            client.start();

            // first request;
            URL url = new URL("http://localhost/test");

            // Prepare the HTTP request.
            HttpRequest request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1, HttpMethod.GET, url.getFile());
            request.headers().set(HttpHeaderNames.HOST, url.getHost());

            Channel channel = client.channel().orElseThrow(() -> new RuntimeException("NoClient"));
            // Send the HTTP request.
            channel.writeAndFlush(request);

            latch.await(10, TimeUnit.SECONDS);

            channel.close().await();
        }
        finally
        {
            server.stop();

            if (client != null)
                client.stop();
        }
    }

    private static boolean shouldRun()
    {
        return Epoll.isAvailable() || KQueue.isAvailable();
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

    @Path("/")
    public static class Resource
    {
        @GET
        @Path("/test")
        @Produces(MediaType.TEXT_PLAIN)
        public String get()
        {
            return "hello world";
        }
    }
}
