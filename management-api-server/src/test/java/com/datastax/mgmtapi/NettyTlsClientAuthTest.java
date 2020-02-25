/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.helpers.NettyHttpIPCClient;
import com.datastax.mgmtapi.util.SocketUtils;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.http.HttpStatus;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.test.TestPortProvider;

import static org.jboss.resteasy.test.TestPortProvider.generateURL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class NettyTlsClientAuthTest
{
    static String BASE_URI = generateURL("");

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testHTTP() throws Throwable
    {
        NettyJaxrsServer netty = new NettyJaxrsServer();
        ResteasyDeployment deployment = new ResteasyDeploymentImpl();
        netty.setDeployment(deployment);
        netty.setPort(TestPortProvider.getPort());
        netty.setRootResourcePath("");
        netty.setSecurityDomain(null);
        netty.start();
        deployment.getRegistry().addSingletonResource(new NettyHttpOverIPCTest.Resource());
        clientCall(null);
        netty.stop();
    }

    @Test(expected = AssertionError.class)
    public void testHttpsWithNoAuthClient() throws Throwable
    {
        File serverKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_ca.key");
        File serverCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_ca.pem");
        String serverKeyPassword = null;

        testHTTPS(serverCrtFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                null, null, null, null);
    }

    @Test
    public void testSharedCert() throws Throwable
    {
        File serverKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_ca.key");
        File serverCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_ca.pem");
        String serverKeyPassword = null;

        testHTTPS(serverCrtFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                serverCrtFile, serverKeyFile, serverCrtFile, serverKeyPassword);
    }

    @Test
    public void testTrustedChainCert() throws Throwable
    {
        File trustCertFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_ca.pem");
        File serverKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_server.key");
        File serverCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_server.crt");
        String serverKeyPassword = null;

        testHTTPS(trustCertFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                trustCertFile, serverKeyFile, serverCrtFile, serverKeyPassword);
    }

    @Test
    public void testTrustedChainSepServerClientCerts() throws Throwable
    {
        File trustCertFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_client_cert_chain.pem");
        File serverKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_server.key");
        File serverCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_server.crt");
        String serverKeyPassword = null;

        File trustFileIntermediate = IntegrationTestUtils.getFile(getClass(), "mutual_auth_client_cert_chain.pem");
        File clientKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_client.key");
        File clientCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_client.crt");
        String clientKeyPassword = null;

        testHTTPS(trustCertFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                trustFileIntermediate, clientKeyFile, clientCrtFile, clientKeyPassword);
    }


    @Test(expected = SSLException.class)
    public void testTrustedChainUntrustedClientCert() throws Throwable
    {
        File trustCertFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_client_cert_chain.pem");
        File serverKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_server.key");
        File serverCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_server.crt");
        String serverKeyPassword = null;

        File trustFileIntermediate = IntegrationTestUtils.getFile(getClass(), "mutual_auth_invalid_client_cert_chain.pem");
        File clientKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_invalid_client.key");
        File clientCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_invalid_client.crt");
        String clientKeyPassword = null;

        testHTTPS(trustCertFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                trustFileIntermediate, clientKeyFile, clientCrtFile, clientKeyPassword);
    }


    @Test(expected = SSLException.class)
    public void testTrustedChainBadClientCert() throws Throwable
    {
        File trustCertFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_client_cert_chain.pem");
        File serverKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_server.key");
        File serverCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_server.crt");
        String serverKeyPassword = null;

        File clientKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_invalid_client.key");
        File clientCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_invalid_client.crt");
        String clientKeyPassword = null;

        testHTTPS(trustCertFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                trustCertFile, clientKeyFile, clientCrtFile, clientKeyPassword);
    }

    public void testHTTPS( File servertTrustCrtFile, File serverKeyFile, final File serverCrtFile, String serverKeyPassword,
            File clientTrustCrtFile, File clientKeyFile, final File clientCrtFile, String clientKeyPassword)
            throws Throwable
    {
        SslContext serverSslCtx =
                SslContextBuilder.forServer(serverCrtFile, serverKeyFile, serverKeyPassword)
                        .clientAuth(ClientAuth.REQUIRE)
                        .trustManager(servertTrustCrtFile)
                        .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                        .sessionCacheSize(0)
                        .sessionTimeout(0).build();

        SslContext clientSslCtx = clientCrtFile == null ? null :
                SslContextBuilder.forClient()
                        .trustManager(clientTrustCrtFile)
                        .keyManager(clientCrtFile, clientKeyFile, clientKeyPassword)
                        .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                        .sessionCacheSize(0)
                        .sessionTimeout(0).build();

        NettyJaxrsServer netty = new NettyJaxrsTLSServer(serverSslCtx);
        ResteasyDeployment deployment = new ResteasyDeploymentImpl();
        netty.setDeployment(deployment);
        netty.setPort(TestPortProvider.getPort());
        netty.setRootResourcePath("");
        netty.setSecurityDomain(null);
        netty.start();
        deployment.getRegistry().addSingletonResource(new NettyHttpOverIPCTest.Resource());
        try
        {
            clientCall(clientSslCtx);
        }
        finally
        {
            netty.stop();
        }
    }

    private void clientCall(SslContext clientSslCtx) throws Throwable
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> serverException = new AtomicReference<>(null);

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {

                            if (clientSslCtx != null)
                                ch.pipeline().addFirst(clientSslCtx.newHandler(ch.alloc()));

                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(4096));
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>()
                            {
                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
                                {
                                    serverException.set(Throwables.getRootCause(cause));
                                    latch.countDown();
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg)
                                {
                                    System.out.println("HTTP response from resteasy: "+msg);
                                    Assert.assertEquals(HttpResponseStatus.OK, msg.status());
                                    latch.countDown();
                                }
                            });
                        }
                    });

            // first request;
            URL url = new URL(BASE_URI+"/test");
            // Make the connection attempt.
            final Channel ch = b.connect(url.getHost(), url.getPort()).sync().channel();

            // Prepare the HTTP request.
            HttpRequest request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1, HttpMethod.GET, url.getFile());
            request.headers().set(HttpHeaderNames.HOST, url.getHost());
            request.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            // Send the HTTP request.
            ch.writeAndFlush(request);

            Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));

            if (serverException.get() != null)
                throw serverException.get();

        } finally {
            // Shut down executor threads to exit.
            group.shutdownGracefully();
        }
    }


    @Test
    public void testManagementAPIWithTLS() throws IOException
    {
        assumeTrue(IntegrationTestUtils.shouldRun());

        String mgmtSock = SocketUtils.makeValidUnixSocketFile(null, "management-netty-tls-mgmt");
        new File(mgmtSock).deleteOnExit();
        String cassSock = SocketUtils.makeValidUnixSocketFile(null, "management-netty-tls-cass");
        new File(cassSock).deleteOnExit();

        List<String> extraArgs = IntegrationTestUtils.getExtraArgs(NettyTlsClientAuthTest.class, "", temporaryFolder.getRoot());

        File trustCertFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_client_cert_chain.pem");
        File serverKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_server.key");
        File serverCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_server.crt");
        String serverKeyPassword = null;

        File trustFileIntermediate = IntegrationTestUtils.getFile(getClass(), "mutual_auth_client_cert_chain.pem");
        File clientKeyFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_client.key");
        File clientCrtFile = IntegrationTestUtils.getFile(getClass(), "mutual_auth_client.crt");
        String clientKeyPassword = null;

        Cli cli = new Cli(Lists.newArrayList("file://" + mgmtSock, BASE_URI), IntegrationTestUtils.getCassandraHome(), cassSock, false, extraArgs,
                trustCertFile.getAbsolutePath(), serverCrtFile.getAbsolutePath(), serverKeyFile.getAbsolutePath());

        cli.preflightChecks();
        Thread cliThread = new Thread(cli);

        try
        {
            cliThread.start();

            URL endpoint = new URL(BASE_URI);
            NettyHttpIPCClient unixClient = new NettyHttpIPCClient(mgmtSock);
            NettyHttpClient httpClient = new NettyHttpClient(endpoint, trustFileIntermediate, clientCrtFile, clientKeyFile);

            //Verify liveness IPC
            boolean live = unixClient.get(URI.create("http://localhost/api/v0/probes/liveness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertTrue(live);

            //Https
            live = httpClient.get(URI.create("http://localhost/api/v0/probes/liveness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertTrue(live);

            //Verify readiness fails IPC
            boolean ready = unixClient.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertFalse(ready);

            //Verify readiness fails HTTPS
            ready = unixClient.get(URI.create("http://localhost/api/v0/probes/readiness").toURL())
                    .thenApply(r -> r.status().code() == HttpStatus.SC_OK).join();

            assertFalse(ready);

        }
        finally
        {
            cli.stop();
            FileUtils.deleteQuietly(new File(cassSock));
            FileUtils.deleteQuietly(new File(mgmtSock));
        }
    }
}
