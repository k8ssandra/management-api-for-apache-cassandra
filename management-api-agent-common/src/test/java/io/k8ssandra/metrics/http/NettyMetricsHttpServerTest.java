/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.http;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.mgmtapi.ipc.NativeTransport;
import io.k8ssandra.metrics.config.Configuration;
import io.k8ssandra.metrics.config.EndpointConfiguration;
import io.k8ssandra.metrics.config.TLSConfiguration;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchService;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NettyMetricsHttpServerTest {
  // Java 11's macOS WatchService polls directories at a ten-second interval.
  private static final int RELOAD_TIMEOUT_SECONDS = 30;

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private EventLoopGroup group;
  private Identity original;
  private Identity renewed;
  private Identity client;
  private Identity newClient;
  private int port;

  @Before
  public void setup() throws Exception {
    group = NativeTransport.tcpEventLoopGroup(1);
    // Use long-lived PEM fixtures, following the REST server TLS tests.
    original = new Identity("mutual_auth_server");
    renewed = new Identity("mutual_auth_client");
    client = original;
    newClient = renewed;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
  }

  @After
  public void teardown() {
    group.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
  }

  @Test
  public void reloadsCertificateKeyAndClientCA() throws Exception {
    Path cert = temporaryFolder.newFolder("cert").toPath().resolve("tls.crt");
    Path key = temporaryFolder.newFolder("key").toPath().resolve("tls.key");
    Path ca = temporaryFolder.newFolder("ca").toPath().resolve("ca.crt");
    copy(original.certificate().toPath(), cert);
    copy(original.privateKey().toPath(), key);
    copy(client.certificate().toPath(), ca);
    start(cert, key, ca);
    assertEquals(original.cert(), scrape(client));
    assertThrows(SSLException.class, () -> scrape(null));

    copy(renewed.privateKey().toPath(), key);
    copy(renewed.certificate().toPath(), cert);
    await()
        .ignoreExceptionsInstanceOf(SSLException.class)
        .atMost(RELOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertEquals(renewed.cert(), scrape(client)));

    copy(newClient.certificate().toPath(), ca);
    await()
        .ignoreExceptionsInstanceOf(SSLException.class)
        .atMost(RELOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertEquals(renewed.cert(), scrape(newClient)));
    assertThrows(SSLException.class, () -> scrape(client));
  }

  @Test
  public void reloadsProjectedSecretSymlink() throws Exception {
    Path root = temporaryFolder.newFolder("secret").toPath();
    writeSecret(root.resolve("original"), original);
    writeSecret(root.resolve("renewed"), renewed);
    Files.createSymbolicLink(root.resolve("..data"), root.getFileSystem().getPath("original"));
    for (String filename : new String[] {"tls.crt", "tls.key", "ca.crt"}) {
      Files.createSymbolicLink(
          root.resolve(filename), root.getFileSystem().getPath("..data", filename));
    }
    start(root.resolve("tls.crt"), root.resolve("tls.key"), root.resolve("ca.crt"));
    assertEquals(original.cert(), scrape(client));

    Files.createSymbolicLink(root.resolve("..data_tmp"), root.getFileSystem().getPath("renewed"));
    Files.move(
        root.resolve("..data_tmp"),
        root.resolve("..data"),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
    await()
        .ignoreExceptionsInstanceOf(SSLException.class)
        .atMost(RELOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertEquals(renewed.cert(), scrape(client)));
  }

  @Test
  public void keepsLastValidContextAndRetriesFailedReload() throws Exception {
    Path root = temporaryFolder.newFolder("retry").toPath();
    copy(original.certificate().toPath(), root.resolve("tls.crt"));
    copy(original.privateKey().toPath(), root.resolve("tls.key"));
    copy(client.certificate().toPath(), root.resolve("ca.crt"));
    NettyMetricsHttpServer server =
        start(root.resolve("tls.crt"), root.resolve("tls.key"), root.resolve("ca.crt"));
    assertEquals(original.cert(), scrape(client));

    Files.write(root.resolve("tls.key"), "invalid private key".getBytes(StandardCharsets.US_ASCII));
    // Observe both a failed reload and its retry before checking the last valid context.
    verify(server, timeout(RELOAD_TIMEOUT_SECONDS * 1000).atLeast(3)).buildSslContext();
    assertEquals(original.cert(), scrape(client));

    copy(renewed.privateKey().toPath(), root.resolve("tls.key"));
    copy(renewed.certificate().toPath(), root.resolve("tls.crt"));
    await()
        .ignoreExceptionsInstanceOf(SSLException.class)
        .atMost(RELOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertEquals(renewed.cert(), scrape(client)));
  }

  @Test
  public void servesWithoutTls() throws Exception {
    start(null, null, null);
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(3000);
      socket
          .getOutputStream()
          .write(
              "GET /metrics HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                  .getBytes(StandardCharsets.US_ASCII));
      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
      assertEquals("HTTP/1.1 200 OK", reader.readLine());
    }
  }

  @Test
  public void stopsWatcherWhileWaitingForAnEvent() throws Exception {
    WatchService watcher = mock(WatchService.class);
    CountDownLatch waitingForEvent = new CountDownLatch(1);
    when(watcher.take())
        .thenAnswer(
            invocation -> {
              waitingForEvent.countDown();
              new CountDownLatch(1).await();
              return null;
            });
    Channel channel = mock(Channel.class);
    ChannelPromise closed = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
    when(channel.closeFuture()).thenReturn(closed);
    NettyHttpInitializer initializer = spy(new NettyHttpInitializer(null));
    CountDownLatch cleanedUp = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              cleanedUp.countDown();
              return null;
            })
        .when(initializer)
        .setSslContext(null);

    new NettyMetricsHttpServer(new Configuration()).startSslWatcher(watcher, initializer, channel);
    try {
      assertTrue(
          "Watcher should wait for a filesystem event", waitingForEvent.await(5, TimeUnit.SECONDS));
    } finally {
      closed.setSuccess();
    }
    assertTrue(
        "Closing the server should stop the watcher and release its context",
        cleanedUp.await(5, TimeUnit.SECONDS));
    verify(watcher).close();
  }

  private void writeSecret(Path dir, Identity server) throws Exception {
    Files.createDirectory(dir);
    copy(server.certificate().toPath(), dir.resolve("tls.crt"));
    copy(server.privateKey().toPath(), dir.resolve("tls.key"));
    copy(client.certificate().toPath(), dir.resolve("ca.crt"));
  }

  private NettyMetricsHttpServer start(Path cert, Path key, Path ca) {
    TLSConfiguration tls = mock(TLSConfiguration.class);
    if (cert != null) {
      when(tls.getTlsCertPath()).thenReturn(cert.toString());
      when(tls.getTlsKeyPath()).thenReturn(key.toString());
      when(tls.getCaCertPath()).thenReturn(ca.toString());
    }
    EndpointConfiguration endpoint = mock(EndpointConfiguration.class);
    when(endpoint.getHost()).thenReturn("127.0.0.1");
    when(endpoint.getPort()).thenReturn(port);
    when(endpoint.getTlsConfig()).thenReturn(cert == null ? null : tls);
    Configuration config = mock(Configuration.class);
    when(config.getEndpointConfiguration()).thenReturn(endpoint);
    NettyMetricsHttpServer server = spy(new NettyMetricsHttpServer(config));
    server.start(group);
    return server;
  }

  private X509Certificate scrape(Identity identity) throws Exception {
    SslContextBuilder builder =
        SslContextBuilder.forClient()
            .sslProvider(SslProvider.JDK)
            .trustManager(InsecureTrustManagerFactory.INSTANCE);
    if (identity != null) {
      builder.keyManager(identity.certificate(), identity.privateKey());
    }
    // A fresh context prevents session resumption from hiding certificate or CA changes.
    JdkSslContext context = (JdkSslContext) builder.build();
    try (SSLSocket socket =
        (SSLSocket) context.context().getSocketFactory().createSocket("127.0.0.1", port)) {
      socket.setSoTimeout(3000);
      socket.startHandshake();
      socket
          .getOutputStream()
          .write(
              "GET /metrics HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                  .getBytes(StandardCharsets.US_ASCII));
      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
      assertEquals("HTTP/1.1 200 OK", reader.readLine());
      return (X509Certificate) socket.getSession().getPeerCertificates()[0];
    }
  }

  private static class Identity {
    private final File certificate;
    private final File privateKey;
    private final X509Certificate cert;

    Identity(String name) throws Exception {
      certificate = new File(NettyMetricsHttpServerTest.class.getResource(name + ".crt").toURI());
      privateKey = new File(NettyMetricsHttpServerTest.class.getResource(name + ".key").toURI());
      try (FileInputStream input = new FileInputStream(certificate)) {
        cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
      }
    }

    File certificate() {
      return certificate;
    }

    File privateKey() {
      return privateKey;
    }

    X509Certificate cert() {
      return cert;
    }
  }

  private static void copy(Path source, Path target) throws Exception {
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
  }
}
