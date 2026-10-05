/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.http;

import com.datastax.mgmtapi.ipc.NativeTransport;
import com.google.common.annotations.VisibleForTesting;
import io.k8ssandra.metrics.config.Configuration;
import io.k8ssandra.metrics.config.EndpointConfiguration;
import io.k8ssandra.metrics.config.TLSConfiguration;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyMetricsHttpServer {

  public static final int DEFAULT_METRICS_PORT = 9000;

  private static final Logger logger = LoggerFactory.getLogger(NettyMetricsHttpServer.class);

  private final Configuration config;

  public NettyMetricsHttpServer(Configuration config) {
    this.config = config;
  }

  public void start(EventLoopGroup group) {
    final WatchService watcher;
    try {
      watcher = createSslWatcher();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    final NettyHttpInitializer initializer;
    try {
      initializer = new NettyHttpInitializer(buildSslContext());
    } catch (SSLException | CertificateException | RuntimeException e) {
      closeWatcher(watcher);
      throw new RuntimeException(e);
    }

    ServerBootstrap b = new ServerBootstrap();
    ServerBootstrap channel =
        b.group(group)
            .childHandler(initializer)
            .channel(NativeTransport.tcpServerSocketChannelClass());

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

    try {
      ChannelFuture bind;
      if (host != null) {
        bind = channel.bind(host, port);
      } else {
        bind = channel.bind(port);
      }
      Channel serverChannel = bind.syncUninterruptibly().channel();
      if (watcher != null) {
        startSslWatcher(watcher, initializer, serverChannel);
      }
    } catch (RuntimeException e) {
      closeWatcher(watcher);
      initializer.setSslContext(null);
      throw e;
    }
  }

  private WatchService createSslWatcher() throws IOException {
    if (config.getEndpointConfiguration() == null
        || config.getEndpointConfiguration().getTlsConfig() == null) {
      return null;
    }
    TLSConfiguration tls = config.getEndpointConfiguration().getTlsConfig();
    Set<Path> directories = new HashSet<>();
    for (String file :
        new String[] {tls.getTlsCertPath(), tls.getTlsKeyPath(), tls.getCaCertPath()}) {
      directories.add(Paths.get(file).toAbsolutePath().getParent());
    }
    WatchService watcher = FileSystems.getDefault().newWatchService();
    try {
      for (Path directory : directories) {
        directory.register(
            watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY);
      }
      return watcher;
    } catch (IOException | RuntimeException e) {
      closeWatcher(watcher);
      throw e;
    }
  }

  @VisibleForTesting
  void startSslWatcher(
      WatchService watcher, NettyHttpInitializer initializer, Channel serverChannel) {
    Thread watcherThread =
        new Thread(
            () -> {
              boolean reloadNeeded = false;
              try {
                while (!Thread.currentThread().isInterrupted()) {
                  WatchKey key = reloadNeeded ? watcher.poll(1, TimeUnit.SECONDS) : watcher.take();
                  while (key != null) {
                    // Also reload for Kubernetes' atomic ..data symlink swap and overflow events.
                    reloadNeeded |= !key.pollEvents().isEmpty();
                    if (!key.reset()) {
                      logger.warn("Metrics TLS certificate directory is no longer available");
                    }
                    key = watcher.poll();
                  }
                  if (reloadNeeded) {
                    try {
                      initializer.setSslContext(buildSslContext());
                      reloadNeeded = false;
                      logger.info("Reloaded metrics SSL/TLS certificates");
                    } catch (SSLException | CertificateException | RuntimeException e) {
                      // Keep serving with the last valid context and retry partial file updates.
                      logger.warn("Unable to reload metrics SSL/TLS certificates; will retry", e);
                    }
                  }
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } catch (ClosedWatchServiceException e) {
                // The filesystem watcher has been closed.
              } finally {
                closeWatcher(watcher);
                initializer.setSslContext(null);
              }
            },
            "metrics-tls-watcher");
    watcherThread.setDaemon(true);
    watcherThread.start();
    serverChannel.closeFuture().addListener(future -> watcherThread.interrupt());
  }

  private static void closeWatcher(WatchService watcher) {
    if (watcher != null) {
      try {
        watcher.close();
      } catch (IOException e) {
        logger.warn("Unable to close metrics TLS filesystem watcher", e);
      }
    }
  }

  @VisibleForTesting
  SslContext buildSslContext() throws SSLException, CertificateException {
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
