package io.k8ssandra.metrics.http;

import io.k8ssandra.metrics.config.Configuration;
import io.k8ssandra.metrics.config.TLSConfiguration;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.net.ssl.SSLException;
import java.io.File;
import java.security.cert.CertificateException;

public class NettyMetricsHttpServer {

    public static final int DEFAULT_METRICS_PORT = 9000;

    private int port = DEFAULT_METRICS_PORT;

    private Configuration config;

    public NettyMetricsHttpServer(Configuration config) {
        this.config = config;
    }

    public void start(EventLoopGroup group) {
        if(config.getPort() > 0) {
            port = config.getPort();
        }

        // Configure SSL.
        final SslContext sslCtx;
        try {
            sslCtx = this.buildSslContext();
        } catch (SSLException | CertificateException e) {
            throw new RuntimeException(e);
        }

        ServerBootstrap b = new ServerBootstrap();
        ServerBootstrap channel = b.group(group)
                .childHandler(new NettyHttpInitializer(sslCtx))
                .channel(EpollServerSocketChannel.class);
        channel.bind(port).syncUninterruptibly().channel();
    }

    private SslContext buildSslContext() throws SSLException, CertificateException {
        TLSConfiguration tlsConfig = config.getTlsConfig();
        if(tlsConfig != null) {
            return SslContextBuilder.forServer(new File(tlsConfig.getTlsCertPath()), new File(tlsConfig.getTlsKeyPath()))
                    .trustManager(new File(tlsConfig.getCaCertPath()))
                    .clientAuth(ClientAuth.REQUIRE)
                    .build();
        }

        return null;
    }
}
