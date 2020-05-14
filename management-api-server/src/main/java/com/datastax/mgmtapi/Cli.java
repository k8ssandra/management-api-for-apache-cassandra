/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Uninterruptibles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.util.ShellUtils;
import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.SingleCommand;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.help.Copyright;
import com.github.rvesse.airline.annotations.help.License;
import com.github.rvesse.airline.annotations.restrictions.Path;
import com.github.rvesse.airline.annotations.restrictions.Required;
import com.github.rvesse.airline.help.Help;
import com.github.rvesse.airline.parser.ParseResult;
import com.github.rvesse.airline.parser.errors.ParseException;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.AttributeKey;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.plugins.server.netty.NettyUtil;
import org.jboss.resteasy.spi.ResteasyDeployment;

@Copyright(startYear = 2020, holder = "DataStax")
@License(url = "https://www.apache.org/licenses/LICENSE-2.0")
@Command(name = "cassandra-management-api", description = "REST service for managing an Apache Cassandra node")
public class Cli implements Runnable
{
    public static final String PROTOCOL_TLS_V1_2 = "TLSv1.2";

    static
    {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
    }

    private static final Logger logger = LoggerFactory.getLogger(Cli.class);
    private ScheduledExecutorService scheduledTasks = null;
    private ScheduledFuture keepAliveTask = null;

    @Inject
    HelpOption<Cli> help;

    @Path
    @Required
    @Option(name = {"-S", "--cassandra-socket"},
            arity = 1,
            description = "Path to Cassandra unix socket file (required)")
    private String cassandra_unix_socket_file = "/var/run/cassandra.sock";

    @Required
    @Option(name = {"-H", "--host"},
            description = "Daemon socket(s) to listen on. (required)")
    private List<String> listen_address = new ArrayList<>();

    @Path
    @Option(name = {"-p", "--pidfile"},
            arity = 1,
            description = "Create a PID file at this file path.")
    private String pidfile = null;

    @Path(executable = true)
    @Option(name = {"-C", "--cassandra-home"},
            arity = 1,
            description = "Path to the cassandra root directory, if missing will use $CASSANDRA_HOME")
    private String cassandra_home;

    @Option(name = {"-K", "--no-keep-alive"},
            arity = 1,
            description = "Setting this flag will stop the management api from starting or keeping Cassandra up automatically")
    private boolean no_keep_alive = false;

    @Option(name = {"--explicit-start"},
            arity = 1,
            description = "When using keep-alive, setting this flag will make the management api wait to start Cassandra until /start is called via REST")
    private boolean explicit_start = false;

    @Path(writable = false)
    @Option(name = {"--tlscacert"},
            arity = 1,
            description = "Path to trust certs signed only by this CA")
    private String tls_ca_cert_file;

    @Path(writable = false)
    @Option(name = {"--tlscert"},
            arity = 1,
            description = "Path to TLS certificate file")
    private String tls_cert_file;

    @Path(writable = false)
    @Option(name = {"--tlskey"},
            arity = 1,
            description = "Path to TLS key file")
    private String tls_key_file;


    private boolean useTls = false;
    private File cassandraUnixSocketFile = null;
    private File cassandraHomeDir = null;
    private File cassandraExe = null;
    private Collection<String> cassandraExtraArgs = Collections.emptyList();
    private ManagementApplication application = null;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private List<NettyJaxrsServer> servers = new ArrayList<>();

    public Cli()
    {

    }

    @VisibleForTesting
    public Cli(List<String> listenAddresses, String cassandraHomeDir, String cassandraUnixSocketFile, boolean keepAlive, Collection<String> cassandraExtraArgs)
    {
        this(listenAddresses, cassandraHomeDir, cassandraUnixSocketFile, keepAlive, cassandraExtraArgs, null, null, null);
    }

    @VisibleForTesting
    public Cli(List<String> listenAddresses, String cassandraHomeDir, String cassandraUnixSocketFile, boolean keepAlive,
            Collection<String> cassandraExtraArgs, String caCertFile, String certFile, String certKeyFile)
    {
        this.listen_address = listenAddresses;
        this.cassandra_home = cassandraHomeDir;
        this.cassandra_unix_socket_file = cassandraUnixSocketFile;
        this.no_keep_alive = !keepAlive;
        this.cassandraExtraArgs = cassandraExtraArgs;
        this.tls_ca_cert_file = caCertFile;
        this.tls_cert_file = certFile;
        this.tls_key_file = certKeyFile;
    }

    @Override
    public void run()
    {
        if (listen_address.isEmpty())
        {
            System.err.println("Requires at least one host to start");
            System.exit(1);
        }

        preflightChecks();

        application = new ManagementApplication(cassandraHomeDir, cassandraExe, cassandraUnixSocketFile, new CqlService(), cassandraExtraArgs);

        try
        {
            for (String uriString : listen_address)
            {
                URI uri = URI.create(uriString);
                NettyJaxrsServer server = null;

                if ((uri.getScheme().equals("file") || uri.getScheme().equals("unix")) && !uri.getPath().isEmpty())
                {
                    server = startHTTPService(new File(uri.getPath()));
                }
                else if (uri.getScheme().equals("tcp") || uri.getScheme().equals("http") || uri.getScheme().equals("https"))
                {
                    server = startHTTPService(uri.getHost(), uri.getPort());
                }
                else
                {
                    System.err.println("Unknown URI scheme: " + uriString);
                    for (NettyJaxrsServer s : servers)
                        s.stop();

                    System.exit(1);
                }

                try
                {
                    server.start();
                    servers.add(server);
                    System.out.println("Started service on " + uriString);
                }
                catch (Throwable t)
                {
                    System.err.println("Error starting server on: " + uri.getPath());
                    t.printStackTrace();
                    for (NettyJaxrsServer s : servers)
                        s.stop();

                    System.exit(2);
                }
            }

            if (!no_keep_alive)
            {
                if (explicit_start) {
                    application.setRequestedState(ManagementApplication.STATE.STOPPED);
                }
                scheduledTasks = Executors.newSingleThreadScheduledExecutor();
                keepAliveTask = scheduledTasks.scheduleAtFixedRate(() -> application.checkState(), 10, 30, TimeUnit.SECONDS);
            }

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            Uninterruptibles.awaitUninterruptibly(shutdownLatch);
        }
        catch (Throwable t)
        {
            // Errors should be being collected so if anything is thrown it is unexpected
            System.err.println(String.format("Unexpected error: %s", t.getMessage()));
            t.printStackTrace(System.err);
        }
    }

    public void stop()
    {
        if (shutdownLatch.getCount() > 0)
        {
            if (keepAliveTask != null)
                keepAliveTask.cancel(true);

            for (NettyJaxrsServer s : servers)
                s.stop();

            shutdownLatch.countDown();
        }
    }

    void checkNettyDeps()
    {
        if (PlatformDependent.isWindows())
        {
            System.err.println("Management API is not supported under Windows");
            System.exit(3);
        }

        if (PlatformDependent.isOsx())
        {
            if (!KQueue.isAvailable())
            {
                System.err.println("Missing KQueue netty libraries");
                System.exit(3);
            }
        }
        else
        {
            if (!Epoll.isAvailable())
            {
                System.err.println("Missing Epoll netty libraries");
                System.exit(3);
            }
        }
    }


    private void checkCassandraCmd()
    {
        try
        {
            if (cassandra_home != null)
            {
                cassandraHomeDir = new File(cassandra_home);
            }
            else if (System.getenv("CASSANDRA_HOME") != null)
            {
                cassandraHomeDir = new File(System.getenv("CASSANDRA_HOME"));
            }

            Optional<File> exe = UnixCmds.which("cassandra");
            exe.ifPresent(file -> cassandraExe = file);

            if (cassandraHomeDir != null && (!cassandraHomeDir.exists() || !cassandraHomeDir.isDirectory()))
                cassandraHomeDir = null;

            if (cassandraHomeDir != null)
            {
                File maybeCassandra = Paths.get(cassandraHomeDir.getAbsolutePath(), "bin", "cassandra").toFile();
                if (maybeCassandra.exists() && maybeCassandra.canExecute())
                    cassandraExe = maybeCassandra;
            }

            if (cassandraExe == null)
                throw new IllegalArgumentException("Unable to locate cassandra executable, set $CASSANDRA_HOME or use -C");

            //Verify Cassandra cmd works
            List<String> errorOutput = new ArrayList<>();
            String version = ShellUtils.executeShellWithHandlers(
                    cassandraExe.getAbsolutePath() + " -v",
                    (input, err) -> input.readLine(),
                    (exitCode, err) -> {
                        String s;
                        errorOutput.add("'" + cassandraExe.getAbsolutePath() + " -v' exit code: " + exitCode);
                        while ((s = err.readLine()) != null)
                            errorOutput.add(s);
                        return null;
                    });


            if (version == null)
                throw new IllegalArgumentException("Version check failed. stderr: " + String.join("\n", errorOutput));

            logger.info("Cassandra Version {}", version);
        }
        catch (IllegalArgumentException e)
        {
            logger.error("Error encountered:", e);
            logger.error("Unable to start: unable to find or execute bin/cassandra " + (cassandra_home == null ? "use -C" : cassandra_home));
            System.exit(3);
        }
        catch (IOException io)
        {
            logger.error("Unknown error", io);
            System.exit(4);
        }
    }

    void checkUnixSocket()
    {
        try
        {
            cassandraUnixSocketFile = Paths.get(cassandra_unix_socket_file).toFile();
        }
        catch (InvalidPathException e)
        {
            logger.error("Unable to start: cassandra_unix_socket_file is not a valid file path: " + cassandra_unix_socket_file);
            System.exit(3);
        }
    }

    void checkTLSDeps()
    {
        boolean hasAny = false;

        // CA CERT File Checks
        if (tls_ca_cert_file != null)
        {
            hasAny = true;
            if (!Files.exists(Paths.get(tls_ca_cert_file)))
            {
                logger.error("Specified CA Cert file does not exist: {}", tls_ca_cert_file);
                System.exit(10);
            }
        }

        // CERT File Checks
        if (tls_cert_file == null && hasAny)
        {
            logger.error("TLS Cert file is required when CA Cert flag is used");
            System.exit(11);
        }

        if (tls_cert_file != null && !hasAny)
        {
            logger.error("TLS CA Cert file is required when Cert flag is used");
            System.exit(12);
        }

        if (tls_cert_file != null)
        {
            hasAny = true;
            if (!Files.exists(Paths.get(tls_cert_file)))
            {
                logger.error("Specified Cert file does not exist: {}", tls_cert_file);
                System.exit(13);
            }
        }

        // KEY File Checks
        if (tls_key_file == null && hasAny)
        {
            logger.error("TLS Key file is required when CA Cert flag is used");
            System.exit(14);
        }

        if (tls_key_file != null && !hasAny)
        {
            logger.error("TLS CA Key file is required when Cert flag is used");
            System.exit(15);
        }

        if (tls_key_file != null)
        {
            if (!Files.exists(Paths.get(tls_key_file)))
            {
                logger.error("Specified Key file does not exist: {}", tls_key_file);
                System.exit(16);
            }
        }

        useTls = hasAny;
    }

    void preflightChecks()
    {
        checkNettyDeps();
        checkTLSDeps();
        checkCassandraCmd();
        checkUnixSocket();
    }

    private NettyJaxrsServer startHTTPService(String hostname, int port) throws SSLException
    {
        NettyJaxrsServer server;

        if (useTls)
        {
            SslContext sslContext = SslContextBuilder
                    .forServer(new File(tls_cert_file), new File(tls_key_file))
                    .trustManager(new File(tls_ca_cert_file))
                    .clientAuth(ClientAuth.REQUIRE)
                    .protocols(PROTOCOL_TLS_V1_2)
                    .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                    .build();

            server = new NettyJaxrsTLSServer(sslContext);
        }
        else
        {
            server = new NettyJaxrsServer();
        }

        ResteasyDeployment deployment = new ResteasyDeploymentImpl();
        deployment.setApplication(application);
        server.setDeployment(deployment);
        server.setRootResourcePath("");
        server.setIdleTimeout(60);
        server.setSecurityDomain(null);
        server.setChannelOptions(ImmutableMap.of(ChannelOption.SO_REUSEADDR, true));
        server.setHostname(hostname);
        server.setPort(port);
        server.setHttpChannelHandlers(accessLogHandlers(useTls ? "https" : "http"));

        return server;
    }

    private NettyJaxrsServer startHTTPService(File socketFile)
    {
        EventLoopGroup loopGroup = PlatformDependent.isOsx() ? new KQueueEventLoopGroup(2) : new EpollEventLoopGroup(2);

        NettyJaxrsServer server = new NettyJaxrsIPCServer(loopGroup, socketFile);
        ResteasyDeployment deployment = new ResteasyDeploymentImpl();
        deployment.setApplication(application);

        server.setDeployment(deployment);
        server.setRootResourcePath("");
        server.setIdleTimeout(60);
        server.setSecurityDomain(null);
        server.setHttpChannelHandlers(accessLogHandlers("unix"));

        return server;
    }


    public static void main(String[] args) {
        SingleCommand<Cli> parser = SingleCommand.singleCommand(Cli.class);
        try
        {
            ParseResult<Cli> result = parser.parseWithResult(args);

            if (result.wasSuccessful())
            {
                // Parsed successfully, so just run the command and exit
                result.getCommand().run();
            }
            else
            {
                // Parsing failed
                // Display errors and then the help information
                System.err.println(String.format("%d errors encountered:", result.getErrors().size()));
                int i = 1;
                for (ParseException e : result.getErrors())
                {
                    System.err.println(String.format("Error %d: %s", i, e.getMessage()));
                    i++;
                }

                System.err.println();

                result.getCommand().help.showHelp();
            }
        }
        catch (ParseException p)
        {
            //noinspection StatementWithEmptyBody
            if (args.length > 0 && (args[0].equals("-h") || args[0].equals("--help")))
            {
                // don't tell the user it's a usage error
                // a bug in airline (https://github.com/airlift/airline/issues/44) prints a usage error even if
                // a user just uses -h/--help
            }
            else
            {
                System.err.println(String.format("Usage error: %s", p.getMessage()));
                System.err.println();
            }

            try
            {
                Help.help(parser.getCommandMetadata(), System.err);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        catch (Exception e)
        {
            // Errors should be being collected so if anything is thrown it is unexpected
            System.err.println(String.format("Unexpected error: %s", e.getMessage()));
            e.printStackTrace(System.err);
        }
    }

    List<ChannelHandler> accessLogHandlers(String protocol)
    {
        @ChannelHandler.Sharable
        class AccessLogInbound extends ChannelInboundHandlerAdapter
        {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
            {
                if (msg instanceof HttpRequest)
                {
                    HttpRequest r = (HttpRequest) msg;
                    ctx.channel().attr(AttributeKey.valueOf("URIINFO")).set(NettyUtil.extractUriInfo(r, "", protocol).getPath());
                }

                super.channelRead(ctx, msg);
            }
        }

        @ChannelHandler.Sharable
        class AccessLogOutbound extends ChannelOutboundHandlerAdapter
        {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
            {
                if (msg instanceof HttpResponse)
                {
                    HttpResponse r = (HttpResponse) msg;
                    SocketAddress addr = ctx.channel().remoteAddress();
                    logger.info("address={} url={} status={}", addr == null ? protocol : addr, ctx.channel().attr(AttributeKey.valueOf("URIINFO")).get(), r.getStatus());
                }

                super.write(ctx, msg, promise);
            }
        }

        return ImmutableList.of(new AccessLogInbound(), new AccessLogOutbound());
    }

}
