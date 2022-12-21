package io.k8ssandra.metrics.http;

import io.k8ssandra.metrics.config.Configuration;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

public class NettyMetricsHttpServer {

    public static final int DEFAULT_METRICS_PORT = 9000;

    private int port = DEFAULT_METRICS_PORT;

    public NettyMetricsHttpServer(Configuration config) {
        if(config.getPort() > 0) {
            port = config.getPort();
        }
    }

    public void start(EventLoopGroup group) {
        // Configure SSL.
//        final SslContext sslCtx = buildSslContext();
        ServerBootstrap b = new ServerBootstrap();
        ServerBootstrap channel = b.group(group)
                .childHandler(new NettyHttpInitializer(null))
                .channel(EpollServerSocketChannel.class);
        channel.bind(port).syncUninterruptibly().channel();
    }

    // TODO Placeholder.
    public static SslContext buildSslContext() throws SSLException, CertificateException {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        return SslContextBuilder
                .forServer(ssc.certificate(), ssc.privateKey())
                .build();
    }
    public void stop() {
        // Stop EventLoopGroups etc?
    }
}
