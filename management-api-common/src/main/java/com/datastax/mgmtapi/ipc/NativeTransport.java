/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.ipc;

import io.netty.channel.Channel;
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

/**
 * Centralises Netty transport selection: epoll (Linux), kqueue (macOS), or NIO (last resort).
 *
 * <p>Epoll is the preferred transport. Falling back to kqueue or NIO if available.
 *
 * <p>Unix-domain-socket channels ({@link #nativeDomainSocketChannelClass()}, {@link
 * #nativeServerDomainSocketChannelClass()}, {@link #nativeEventLoopGroup(int)}) require native
 * transport (epoll or kqueue) and will throw {@link UnsupportedOperationException} if neither is
 * available, because there is no NIO equivalent for Unix domain sockets.
 *
 * <p>TCP channels ({@link #tcpEventLoopGroup(int)}, {@link #tcpServerSocketChannelClass()}) fall
 * back to NIO when no native transport is available.
 */
public final class NativeTransport {

  private static final boolean EPOLL_AVAILABLE = Epoll.isAvailable();
  private static final boolean KQUEUE_AVAILABLE = KQueue.isAvailable();

  private NativeTransport() {}

  /** Returns {@code true} if epoll or kqueue is available on this platform. */
  public static boolean isNativeTransportAvailable() {
    return EPOLL_AVAILABLE || KQUEUE_AVAILABLE;
  }

  /**
   * Returns a native {@link EventLoopGroup} suitable for Unix domain socket communication.
   *
   * <p>Prefers epoll; falls back to kqueue on macOS/BSD. Throws {@link
   * UnsupportedOperationException} if neither is available, as Unix domain sockets have no NIO
   * fallback.
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
    throw new UnsupportedOperationException("Neither epoll nor kqueue is available.");
  }

  /**
   * Returns the native {@link Channel} class for a Unix domain socket client.
   *
   * <p>Throws {@link UnsupportedOperationException} if native transport is unavailable.
   */
  public static Class<? extends Channel> nativeDomainSocketChannelClass() {
    if (EPOLL_AVAILABLE) {
      return EpollDomainSocketChannel.class;
    }
    if (KQUEUE_AVAILABLE) {
      return KQueueDomainSocketChannel.class;
    }
    throw new UnsupportedOperationException("Neither epoll nor kqueue is available.");
  }

  /**
   * Returns the native {@link Channel} class for a Unix domain socket server.
   *
   * <p>Throws {@link UnsupportedOperationException} if native transport is unavailable.
   */
  public static Class<? extends Channel> nativeServerDomainSocketChannelClass() {
    if (EPOLL_AVAILABLE) {
      return EpollServerDomainSocketChannel.class;
    }
    if (KQUEUE_AVAILABLE) {
      return KQueueServerDomainSocketChannel.class;
    }
    throw new UnsupportedOperationException("Neither epoll nor kqueue is available.");
  }

  /**
   * Returns an {@link EventLoopGroup} for TCP socket connections.
   *
   * <p>Prefers epoll; falls back to kqueue or {@link NioEventLoopGroup}.
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
    return new NioEventLoopGroup(nThreads);
  }

  /**
   * Returns the server socket channel class for TCP connections.
   *
   * <p>Prefers epoll; falls back to kqueue or {@link NioServerSocketChannel}.
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
