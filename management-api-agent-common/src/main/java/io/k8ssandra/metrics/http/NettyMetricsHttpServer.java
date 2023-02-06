/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.http;

import io.k8ssandra.metrics.config.Configuration;
import io.k8ssandra.metrics.config.EndpointConfiguration;
import io.k8ssandra.metrics.config.TLSConfiguration;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLException;

public class NettyMetricsHttpServer {

  public static final int DEFAULT_METRICS_PORT = 9000;

  private Configuration config;

  public NettyMetricsHttpServer(Configuration config) {
    this.config = config;
  }

  public void start(EventLoopGroup group) {
    // Configure SSL.
    final SslContext sslCtx;
    try {
      sslCtx = this.buildSslContext();
    } catch (SSLException | CertificateException e) {
      throw new RuntimeException(e);
    }

    ServerBootstrap b = new ServerBootstrap();
    ServerBootstrap channel =
        b.group(group)
            .childHandler(new NettyHttpInitializer(sslCtx))
            .channel(EpollServerSocketChannel.class);

    int port = DEFAULT_METRICS_PORT;
    String host = null;
    if (config.getEndpointConfiguration() != null) {
      EndpointConfiguration endpointConfiguration = config.getEndpointConfiguration();
      if (endpointConfiguration.getPort() > 0) {
        port = endpointConfiguration.getPort();
      }
      if (endpointConfiguration.getHost() != null) {
        host = endpointConfiguration.getHost();
      }
    }

    ChannelFuture bind;
    if (host != null) {
      bind = channel.bind(host, port);
    } else {
      bind = channel.bind(port);
    }

    bind.syncUninterruptibly().channel();
  }

  private SslContext buildSslContext() throws SSLException, CertificateException {
    if (config.getEndpointConfiguration() == null
        || config.getEndpointConfiguration().getTlsConfig() == null) {
      return null;
    }

    TLSConfiguration tlsConfig = config.getEndpointConfiguration().getTlsConfig();
    return SslContextBuilder.forServer(
            new File(tlsConfig.getTlsCertPath()), new File(tlsConfig.getTlsKeyPath()))
        .trustManager(new File(tlsConfig.getCaCertPath()))
        .clientAuth(ClientAuth.REQUIRE)
        .build();
  }
}
