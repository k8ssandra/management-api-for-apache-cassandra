/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package org.apache.cassandra.transport;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * A simple wrapper class around Netty's Channel class. The only job of this class is to override
 * the remoteAddress() method to return null as it causes a ClasCastException in Cassandra for 5.0
 * and newer.
 */
public class NettyChannelWrapper implements Channel {

  private final Channel delegate;

  NettyChannelWrapper(Channel delegate) {
    this.delegate = delegate;
  }

  @Override
  public ChannelId id() {
    return delegate.id();
  }

  @Override
  public EventLoop eventLoop() {
    return delegate.eventLoop();
  }

  @Override
  public Channel parent() {
    return delegate.parent();
  }

  @Override
  public ChannelConfig config() {
    return delegate.config();
  }

  @Override
  public boolean isOpen() {
    return delegate.isOpen();
  }

  @Override
  public boolean isRegistered() {
    return delegate.isRegistered();
  }

  @Override
  public boolean isActive() {
    return delegate.isActive();
  }

  @Override
  public ChannelMetadata metadata() {
    return delegate.metadata();
  }

  @Override
  public SocketAddress localAddress() {
    return delegate.localAddress();
  }

  @Override
  public SocketAddress remoteAddress() {
    /**
     * For Cassandra 5.0, and/or possibly Netty 4.1.96.Final, the remote address for the Channel
     * seems to be initialized to a Netty io.netty.channel.unix.DomainSocketAddress instance. For
     * Cassandra 4.1 and lower, it seems to be null. Strangely enough, an initialized instance of
     * io.netty.channel.unix.DomainSocketAddress causes a ClassCastException in Cassandra, since it
     * does not extend java.io.InetSocketAddress. Apparently, null can be cast as an instance of
     * InetSocketAddress and this works fine for how the Management API Agent works. So, for
     * Cassandra 5.0+, we just return null here.
     */
    SocketAddress realAddress = delegate.remoteAddress();
    if (realAddress != null) {
      if (InetSocketAddress.class.isAssignableFrom(realAddress.getClass())) {
        // remoteAddress can be cast as InetSocketAddress so we can return the delegate's address
        return delegate.remoteAddress();
      }
    }
    // either the remoteAddress was null, or it cannot be cast as an InetSocketAddress.
    return null;
  }

  @Override
  public ChannelFuture closeFuture() {
    return delegate.closeFuture();
  }

  @Override
  public boolean isWritable() {
    return delegate.isWritable();
  }

  @Override
  public long bytesBeforeUnwritable() {
    return delegate.bytesBeforeUnwritable();
  }

  @Override
  public long bytesBeforeWritable() {
    return delegate.bytesBeforeWritable();
  }

  @Override
  public Unsafe unsafe() {
    return delegate.unsafe();
  }

  @Override
  public ChannelPipeline pipeline() {
    return delegate.pipeline();
  }

  @Override
  public ByteBufAllocator alloc() {
    return delegate.alloc();
  }

  @Override
  public Channel read() {
    return delegate.read();
  }

  @Override
  public Channel flush() {
    return delegate.flush();
  }

  @Override
  public <T> Attribute<T> attr(AttributeKey<T> ak) {
    return delegate.attr(ak);
  }

  @Override
  public <T> boolean hasAttr(AttributeKey<T> ak) {
    return delegate.hasAttr(ak);
  }

  @Override
  public ChannelFuture bind(SocketAddress localAddress) {
    return delegate.bind(localAddress);
  }

  @Override
  public ChannelFuture connect(SocketAddress remoteAddress) {
    return delegate.connect(remoteAddress);
  }

  @Override
  public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
    return delegate.connect(remoteAddress, localAddress);
  }

  @Override
  public ChannelFuture disconnect() {
    return delegate.disconnect();
  }

  @Override
  public ChannelFuture close() {
    return delegate.close();
  }

  @Override
  public ChannelFuture deregister() {
    return delegate.deregister();
  }

  @Override
  public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
    return delegate.bind(localAddress, promise);
  }

  @Override
  public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
    return delegate.connect(remoteAddress, promise);
  }

  @Override
  public ChannelFuture connect(
      SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
    return delegate.connect(remoteAddress, localAddress, promise);
  }

  @Override
  public ChannelFuture disconnect(ChannelPromise promise) {
    return delegate.disconnect(promise);
  }

  @Override
  public ChannelFuture close(ChannelPromise promise) {
    return delegate.close(promise);
  }

  @Override
  public ChannelFuture deregister(ChannelPromise promise) {
    return delegate.deregister(promise);
  }

  @Override
  public ChannelFuture write(Object msg) {
    return delegate.write(msg);
  }

  @Override
  public ChannelFuture write(Object msg, ChannelPromise promise) {
    return delegate.write(msg, promise);
  }

  @Override
  public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
    return delegate.writeAndFlush(msg, promise);
  }

  @Override
  public ChannelFuture writeAndFlush(Object msg) {
    return delegate.writeAndFlush(msg);
  }

  @Override
  public ChannelPromise newPromise() {
    return delegate.newPromise();
  }

  @Override
  public ChannelProgressivePromise newProgressivePromise() {
    return delegate.newProgressivePromise();
  }

  @Override
  public ChannelFuture newSucceededFuture() {
    return delegate.newSucceededFuture();
  }

  @Override
  public ChannelFuture newFailedFuture(Throwable cause) {
    return delegate.newFailedFuture(cause);
  }

  @Override
  public ChannelPromise voidPromise() {
    return delegate.voidPromise();
  }

  @Override
  public int compareTo(Channel o) {
    return delegate.compareTo(o);
  }
}
