/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import java.io.StringWriter;
import java.net.URI;

public class NettyServerHandler extends SimpleChannelInboundHandler<HttpObject> {
  private final StringWriter writer = new StringWriter();

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    ctx.flush();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, HttpObject httpObject) throws Exception {
    if (httpObject instanceof HttpRequest) {
      HttpRequest req = (HttpRequest) httpObject;

      URI uri = new URI(req.getUri());
      if (!uri.getPath().equals("/metrics")) {
        // Send 404?
        FullHttpResponse resp =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        // Write the response.
        ctx.channel().writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        return;
      }

      writer.getBuffer().setLength(0);

      String contentType = TextFormat.chooseContentType(req.headers().get("Accept"));
      TextFormat.writeFormat(
          contentType, writer, CollectorRegistry.defaultRegistry.metricFamilySamples());

      if (!writeResponse(req, ctx, contentType)) {
        // If keep-alive is off, close the connection once the content is fully written.
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
      }
    }
  }

  private boolean writeResponse(
      HttpRequest request, ChannelHandlerContext ctx, String contentType) {
    // Decide whether to close the connection or not.
    boolean keepAlive =
        HttpHeaders.isKeepAlive(
            request); // Keep HttpHeaders instead of HttpUtil to get Netty 4.0.x compatibility
    // Build the response object.
    /**
     * TODO We should probably use something more performant.. copyBuffer for single thread
     * (blocking) seems weird
     */
    FullHttpResponse response =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            request.getDecoderResult().isSuccess()
                ? HttpResponseStatus.OK
                : HttpResponseStatus.BAD_REQUEST,
            Unpooled.copiedBuffer(writer.toString(), CharsetUtil.UTF_8));

    response.headers().set(HttpHeaders.Names.CONTENT_TYPE, contentType);

    if (keepAlive) {
      // Add 'Content-Length' header only for a keep-alive connection.
      response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
      // Add keep alive header as per:
      // - https://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
      response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
    }

    // Write the response.
    ctx.write(response);

    return keepAlive;
  }
}
