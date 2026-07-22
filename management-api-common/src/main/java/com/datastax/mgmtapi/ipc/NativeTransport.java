/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.ipc;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralises Netty transport selection: epoll (Linux), kqueue (macOS), or NIO (fallback).
 *
 * <p>Unix-domain-socket channels ({@link #nativeDomainSocketChannelClass()}, {@link
 * #nativeServerDomainSocketChannelClass()}, {@link #nativeEventLoopGroup(int)}) require a native
 * transport and will throw if neither epoll nor kqueue is available.
 *
 * <p>TCP channels ({@link #tcpEventLoopGroup(int)}, {@link #tcpServerSocketChannelClass()}) fall
 * back to Java NIO when no native transport is available. A warning is logged in that case because
 * NIO has lower performance and is only suitable for development/testing/lab environments.
 */
public final class NativeTransport {

  private static final Logger logger = LoggerFactory.getLogger(NativeTransport.class);

  private static final boolean EPOLL_AVAILABLE = Epoll.isAvailable();
  private static final boolean KQUEUE_AVAILABLE = KQueue.isAvailable();

  static final String NIO_FALLBACK_WARNING =
      "Neither epoll nor kqueue is available. Falling back to Java NIO for TCP connections. "
          + "This results in degraded performance and should only be acceptable in "
          + "development, testing, or lab environments. Unix domain socket communication "
          + "requires native transport and will not be available.";

  private NativeTransport() {}

  /** Returns {@code true} if epoll or kqueue is available on this platform. */
  public static boolean isNativeTransportAvailable() {
    return EPOLL_AVAILABLE || KQUEUE_AVAILABLE;
  }

  /**
   * Returns a native {@link EventLoopGroup} suitable for Unix domain socket communication.
   *
   * <p>Uses epoll on Linux and kqueue on macOS/BSD. Throws {@link UnsupportedOperationException} if
   * neither is available, as Unix domain sockets have no NIO fallback.
   *
   * @param nThreads number of threads in the event loop group
   */
  public static EventLoopGroup nativeEventLoopGroup(int nThreads) {
    if (EPOLL_AVAILABLE) {
      return new EpollEventLoopGroup(nThreads);
    }
    if (KQUEUE_AVAILABLE) {
      return new KQueueEventLoopGroup(nThreads);
    }
    throw new UnsupportedOperationException(
        "Neither epoll nor kqueue is available. Unix domain socket communication requires "
            + "native transport (epoll on Linux, kqueue on macOS/BSD).");
  }

  /**
   * Returns the native {@link io.netty.channel.Channel} class for a Unix domain socket client.
   *
   * <p>Throws {@link UnsupportedOperationException} if native transport is unavailable.
   */
  public static Class<? extends io.netty.channel.Channel> nativeDomainSocketChannelClass() {
    if (EPOLL_AVAILABLE) {
      return EpollDomainSocketChannel.class;
    }
    if (KQUEUE_AVAILABLE) {
      return KQueueDomainSocketChannel.class;
    }
    throw new UnsupportedOperationException(
        "Neither epoll nor kqueue is available. Unix domain socket channels require native "
            + "transport.");
  }

  /**
   * Returns the native {@link io.netty.channel.Channel} class for a Unix domain socket server.
   *
   * <p>Throws {@link UnsupportedOperationException} if native transport is unavailable.
   */
  public static Class<? extends io.netty.channel.Channel> nativeServerDomainSocketChannelClass() {
    if (EPOLL_AVAILABLE) {
      return EpollServerDomainSocketChannel.class;
    }
    if (KQUEUE_AVAILABLE) {
      return KQueueServerDomainSocketChannel.class;
    }
    throw new UnsupportedOperationException(
        "Neither epoll nor kqueue is available. Unix domain socket server channels require "
            + "native transport.");
  }

  /**
   * Returns an {@link EventLoopGroup} for TCP socket connections.
   *
   * <p>Prefers native transport (epoll/kqueue) when available. Falls back to Java NIO, logging a
   * warning that this is only appropriate for development/testing environments.
   *
   * @param nThreads number of threads in the event loop group
   */
  public static EventLoopGroup tcpEventLoopGroup(int nThreads) {
    if (EPOLL_AVAILABLE) {
      return new EpollEventLoopGroup(nThreads);
    }
    if (KQUEUE_AVAILABLE) {
      return new KQueueEventLoopGroup(nThreads);
    }
    logger.warn(NIO_FALLBACK_WARNING);
    return new NioEventLoopGroup(nThreads);
  }

  /**
   * Returns the server socket channel class for TCP connections.
   *
   * <p>Prefers native transport when available, falls back to {@link NioServerSocketChannel}.
   */
  public static Class<? extends ServerChannel> tcpServerSocketChannelClass() {
    if (EPOLL_AVAILABLE) {
      return EpollServerSocketChannel.class;
    }
    if (KQUEUE_AVAILABLE) {
      return KQueueServerSocketChannel.class;
    }
    return NioServerSocketChannel.class;
  }
}
