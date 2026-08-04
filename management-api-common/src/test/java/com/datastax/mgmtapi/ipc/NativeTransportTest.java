/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.ipc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Test;

/**
 * Unit tests for {@link NativeTransport}.
 */
public class NativeTransportTest {

  // -------------------------------------------------------------------------
  // isNativeTransportAvailable
  // -------------------------------------------------------------------------

  @Test
  public void isNativeTransportAvailable_returnsTrueOnSupportedPlatform() {
    assumeTrue(NativeTransport.isNativeTransportAvailable());
  }

  // -------------------------------------------------------------------------
  // nativeEventLoopGroup
  // -------------------------------------------------------------------------

  @Test
  public void nativeEventLoopGroup_returnsNativeGroup() throws Exception {
    assumeTrue(NativeTransport.isNativeTransportAvailable());
    EventLoopGroup group = NativeTransport.nativeEventLoopGroup(1);
    try {
      assertNotNull(group);
      assertTrue(
          "Expected epoll or kqueue EventLoopGroup",
          group instanceof EpollEventLoopGroup || group instanceof KQueueEventLoopGroup);
    } finally {
      group.shutdownGracefully().sync();
    }
  }

  // -------------------------------------------------------------------------
  // nativeDomainSocketChannelClass
  // -------------------------------------------------------------------------

  @Test
  public void nativeDomainSocketChannelClass_returnsCorrectClass() {
    assumeTrue(NativeTransport.isNativeTransportAvailable());
    Class<?> cls = NativeTransport.nativeDomainSocketChannelClass();
    assertNotNull(cls);
    assertTrue(cls == EpollDomainSocketChannel.class || cls == KQueueDomainSocketChannel.class);
  }

  // -------------------------------------------------------------------------
  // nativeServerDomainSocketChannelClass
  // -------------------------------------------------------------------------

  @Test
  public void nativeServerDomainSocketChannelClass_returnsCorrectClass() {
    assumeTrue(NativeTransport.isNativeTransportAvailable());
    Class<?> cls = NativeTransport.nativeServerDomainSocketChannelClass();
    assertNotNull(cls);
    assertTrue(
        cls == EpollServerDomainSocketChannel.class
            || cls == KQueueServerDomainSocketChannel.class);
  }

  // -------------------------------------------------------------------------
  // tcpEventLoopGroup — prefers native; falls back to NIO
  // -------------------------------------------------------------------------

  @Test
  public void tcpEventLoopGroup_returnsNativeGroup() throws Exception {
    assumeTrue(NativeTransport.isNativeTransportAvailable());
    EventLoopGroup group = NativeTransport.tcpEventLoopGroup(1);
    try {
      assertNotNull(group);
      assertFalse("Expected native (not NIO) group", group instanceof NioEventLoopGroup);
    } finally {
      group.shutdownGracefully().sync();
    }
  }

  // -------------------------------------------------------------------------
  // tcpServerSocketChannelClass — prefers native; falls back to NIO
  // -------------------------------------------------------------------------

  @Test
  public void tcpServerSocketChannelClass_returnsNativeClass() {
    assumeTrue(NativeTransport.isNativeTransportAvailable());
    Class<? extends ServerChannel> cls = NativeTransport.tcpServerSocketChannelClass();
    assertNotNull(cls);
    assertTrue(cls == EpollServerSocketChannel.class || cls == KQueueServerSocketChannel.class);
  }
}
