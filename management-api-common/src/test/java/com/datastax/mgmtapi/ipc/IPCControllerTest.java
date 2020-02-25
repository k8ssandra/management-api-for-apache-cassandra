/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.ipc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public class IPCControllerTest
{
    private static final Logger logger = LoggerFactory.getLogger(IPCController.class);
    private static final FileAttribute ownerWritable = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--"));

    private ConcurrentMap<UUID, Consumer<String>> callbacks = new ConcurrentHashMap<>();

    @Test
    public void clientServerTest() throws IOException, InterruptedException
    {
        if (!shouldRun())
            return;

        File socketFile = Files.createTempFile("ipc-test-", ".sock", ownerWritable).toFile();
        socketFile.delete();

        logger.info("Socket {}", socketFile);

        IPCController server = IPCController.newServer()
                .withSocketFile(socketFile)
                .withEventLoop( eventLoop())
                .withChannelHandler(new ChannelInitializer<Channel>()
                {
                    @Override
                    protected void initChannel(Channel channel) throws Exception
                    {
                        channel.pipeline()
                                .addLast(new LineBasedFrameDecoder(256))
                                .addLast(new StringDecoder(CharsetUtil.US_ASCII))
                                .addLast(new StringEncoder(CharsetUtil.US_ASCII))
                                .addLast(new SimpleChannelInboundHandler<String>()
                                {
                                    @Override
                                    protected void channelRead0(
                                            ChannelHandlerContext ctx,
                                            String msg
                                    ) throws Exception
                                    {
                                        logger.info("Server read: {}", msg);
                                        ctx.writeAndFlush(msg + "\n");
                                    }
                                });
                    }
                })
                .build();


        IPCController client = IPCController.newClient()
                .withSocketFile(socketFile)
                .withEventLoop(eventLoop())
                .withChannelHandler(new ChannelInitializer<Channel>()
                {
                    @Override
                    protected void initChannel(Channel channel) throws Exception
                    {
                        channel.pipeline()
                                .addLast(new LineBasedFrameDecoder(256))
                                .addLast(new StringDecoder(CharsetUtil.US_ASCII))
                                .addLast(new StringEncoder(CharsetUtil.US_ASCII))
                                .addLast(new SimpleChannelInboundHandler<String>()
                                {
                                    @Override
                                    protected void channelRead0(
                                            ChannelHandlerContext ctx,
                                            String msg
                                    ) throws Exception
                                    {
                                        logger.info("Client read: {}", msg);

                                        int delim = msg.indexOf(" ");

                                        UUID msgId = UUID.fromString(msg.substring(0, delim));

                                        Consumer<String> callback = callbacks.remove(msgId);
                                        callback.accept(msg.substring(delim + 1));
                                    }
                                });
                    }
                })
                .build();

        try
        {
            server.start();
            Assert.assertTrue(server.isActive());

            client.start();
            Assert.assertTrue(client.isActive());

            Channel c = client.channel().orElseThrow(() -> new AssertionError("Channel not active"));
            for (int i = 0; i < 10; i++)
                sendAndCheck(c, "test" + i);
        }
        finally
        {
            server.stop();
            Assert.assertFalse(server.channel().isPresent());

            client.stop();
            Assert.assertFalse(client.channel().isPresent());
        }
    }

    private void sendAndCheck(Channel c, String msg) throws InterruptedException
    {
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        callbacks.put(id, resp -> {
            logger.info("ID {}, Sent {}, Received {}", id, msg, resp);
            Assert.assertEquals(msg, resp);
            latch.countDown();
        });

        String m = id + " " + msg + "\n";
        logger.info("Client sending: {}", m);
        c.writeAndFlush(m);
        latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(latch.getCount() == 0);
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
}
