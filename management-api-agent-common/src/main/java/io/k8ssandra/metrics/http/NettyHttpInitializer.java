/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.util.ReferenceCountUtil;

public class NettyHttpInitializer extends ChannelInitializer<SocketChannel> {
  private SslContext sslCtx;

  public NettyHttpInitializer(SslContext sslCtx) {
    this.sslCtx = sslCtx;
  }

  synchronized void setSslContext(SslContext sslCtx) {
    SslContext previous = this.sslCtx;
    this.sslCtx = sslCtx;
    ReferenceCountUtil.release(previous);
  }

  @Override
  public void initChannel(SocketChannel ch) {
    ChannelPipeline p = ch.pipeline();
    synchronized (this) {
      if (sslCtx != null) {
        p.addLast(sslCtx.newHandler(ch.alloc()));
      }
    }
    p.addLast(new HttpServerCodec());
    // TODO Do we need chunking for larger /metrics ?
    p.addLast(new HttpContentCompressor());
    p.addLast(new NettyServerHandler());
  }
}
