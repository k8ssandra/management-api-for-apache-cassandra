package io.k8ssandra.metrics.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;

public class NettyHttpInitializer extends ChannelInitializer<SocketChannel> {
    private final SslContext sslCtx;

    public NettyHttpInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(ch.alloc()));
        }
        p.addLast(new HttpServerCodec());
//        p.addLast(new HttpObjectAggregator(65536));
        p.addLast(new HttpRequestDecoder());
//        p.addLast(new ChunkedWriteHandler());
        p.addLast(new HttpResponseEncoder());
        p.addLast(new NettyServerHandler());
    }
}
