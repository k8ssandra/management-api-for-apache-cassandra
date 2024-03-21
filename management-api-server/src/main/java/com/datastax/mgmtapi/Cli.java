/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Uninterruptibles;
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
import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.net.ssl.SSLException;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.plugins.server.netty.NettyUtil;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Copyright(startYear = 2020, holder = "DataStax")
@License(url = "https://www.apache.org/licenses/LICENSE-2.0")
@Command(
    name = "cassandra-management-api",
    description = "REST service for managing an Apache Cassandra or DSE node")
public class Cli implements Runnable {

  static {
    InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
  }

  private static final Logger logger = LoggerFactory.getLogger(Cli.class);
  private ScheduledExecutorService scheduledTasks = null;
  private ScheduledFuture keepAliveTask = null;

  @Inject HelpOption<Cli> help;

  @Path
  @Required
  @Option(
      name = {"-S", "--cassandra-socket", "--db-socket"},
      arity = 1,
      description = "Path to Cassandra/DSE unix socket file (required)")
  private String db_unix_socket_file = "/var/run/db.sock";

  @Required
  @Option(
      name = {"-H", "--host"},
      description = "Daemon socket(s) to listen on. (required)")
  private List<String> listen_address = new ArrayList<>();

  @Path
  @Option(
      name = {"-p", "--pidfile"},
      arity = 1,
      description = "Create a PID file at this file path.")
  private String pidfile = null;

  @Path(executable = true)
  @Option(
      name = {"-C", "--cassandra-home", "--db-home"},
      arity = 1,
      description =
          "Path to the Cassandra or DSE root directory, if missing will use $CASSANDRA_HOME/$DSE_HOME respectively")
  private String db_home;

  @Option(
      name = {"-K", "--no-keep-alive"},
      arity = 1,
      description =
          "Setting this flag will stop the management api from starting or keeping Cassandra/DSE up automatically")
  private boolean no_keep_alive = false;

  @Option(
      name = {"--explicit-start"},
      arity = 1,
      description =
          "When using keep-alive, setting this flag will make the management api wait to start Cassandra/DSE until /start is called via REST")
  private boolean explicit_start = false;

  @Path(writable = false)
  @Option(
      name = {"--tlscacert"},
      arity = 1,
      description = "Path to trust certs signed only by this CA")
  private String tls_ca_cert_file;

  private File tlsCaCert;

  @Path(writable = false)
  @Option(
      name = {"--tlscert"},
      arity = 1,
      description = "Path to TLS certificate file")
  private String tls_cert_file;

  private File tlsCert;

  @Path(writable = false)
  @Option(
      name = {"--tlskey"},
      arity = 1,
      description = "Path to TLS key file")
  private String tls_key_file;

  private File tlsKey;

  private boolean useTls = false;
  private File dbUnixSocketFile = null;
  private File dbHomeDir = null;
  private File dbCmdFile = null;
  private Collection<String> dbExtraArgs = Collections.emptyList();
  private ManagementApplication application = null;
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);
  private List<NettyJaxrsServer> servers = new ArrayList<>();

  private SslContext sslContext;

  public Cli() {}

  @VisibleForTesting
  public Cli(
      List<String> listenAddresses,
      String dbHomeDir,
      String dbUnixSocketFile,
      boolean keepAlive,
      Collection<String> dbExtraArgs) {
    this(listenAddresses, dbHomeDir, dbUnixSocketFile, keepAlive, dbExtraArgs, null, null, null);
  }

  @VisibleForTesting
  public Cli(
      List<String> listenAddresses,
      String dbHomeDir,
      String dbUnixSocketFile,
      boolean keepAlive,
      Collection<String> dbExtraArgs,
      String caCertFile,
      String certFile,
      String certKeyFile) {
    this.listen_address = listenAddresses;
    this.db_home = dbHomeDir;
    this.db_unix_socket_file = dbUnixSocketFile;
    this.no_keep_alive = !keepAlive;
    this.dbExtraArgs = dbExtraArgs;
    this.tls_ca_cert_file = caCertFile;
    this.tls_cert_file = certFile;
    this.tls_key_file = certKeyFile;
  }

  @Override
  public void run() {
    if (listen_address.isEmpty()) {
      System.err.println("Requires at least one host to start");
      System.exit(1);
    }

    preflightChecks();

    application =
        new ManagementApplication(
            dbHomeDir, dbCmdFile, dbUnixSocketFile, new CqlService(), dbExtraArgs);

    try {
      for (String uriString : listen_address) {
        URI uri = URI.create(uriString);
        NettyJaxrsServer server = null;

        if ((uri.getScheme().equals("file") || uri.getScheme().equals("unix"))
            && !uri.getPath().isEmpty()) {
          server = startHTTPService(new File(uri.getPath()));
        } else if (uri.getScheme().equals("tcp")
            || uri.getScheme().equals("http")
            || uri.getScheme().equals("https")) {
          server = startHTTPService(uri.getHost(), uri.getPort());
        } else {
          System.err.println("Unknown URI scheme: " + uriString);
          for (NettyJaxrsServer s : servers) s.stop();

          System.exit(1);
        }

        try {
          server.start();
          servers.add(server);
          System.out.println("Started service on " + uriString);
        } catch (Throwable t) {
          System.err.println("Error starting server on: " + uri.getPath());
          t.printStackTrace();
          for (NettyJaxrsServer s : servers) s.stop();

          System.exit(2);
        }
      }

      if (!no_keep_alive) {
        if (explicit_start) {
          application.setRequestedState(ManagementApplication.STATE.STOPPED);
        }
        scheduledTasks = Executors.newSingleThreadScheduledExecutor();
        keepAliveTask =
            scheduledTasks.scheduleAtFixedRate(
                () -> application.checkState(), 10, 30, TimeUnit.SECONDS);
      }

      Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

      Uninterruptibles.awaitUninterruptibly(shutdownLatch);
    } catch (Throwable t) {
      // Errors should be being collected so if anything is thrown it is unexpected
      System.err.println(String.format("Unexpected error: %s", t.getMessage()));
      t.printStackTrace(System.err);
    }
  }

  public void stop() {
    if (shutdownLatch.getCount() > 0) {
      if (keepAliveTask != null) keepAliveTask.cancel(true);

      for (NettyJaxrsServer s : servers) s.stop();

      shutdownLatch.countDown();
    }
  }

  void checkNettyDeps() {
    if (PlatformDependent.isWindows()) {
      System.err.println("Management API is not supported under Windows");
      System.exit(3);
    }

    if (PlatformDependent.isOsx()) {
      if (!KQueue.isAvailable()) {
        System.err.println("Missing KQueue netty libraries");
        System.exit(3);
      }
    } else {
      if (!Epoll.isAvailable()) {
        System.err.println("Missing Epoll netty libraries");
        System.exit(3);
      }
    }
  }

  private void checkDbCmd() {
    String dbCmd = "cassandra";
    try {
      boolean isDse = isDse();

      String dbHomeEnv = isDse ? "DSE_HOME" : "CASSANDRA_HOME";
      if (db_home != null) {
        dbHomeDir = new File(db_home);
      } else if (System.getenv(dbHomeEnv) != null) {
        dbHomeDir = new File(System.getenv(dbHomeEnv));
      }

      Optional<File> exe = isDse ? UnixCmds.whichDse() : UnixCmds.whichCassandra();
      exe.ifPresent(file -> dbCmdFile = file);

      if (dbHomeDir != null && (!dbHomeDir.exists() || !dbHomeDir.isDirectory())) dbHomeDir = null;

      if (dbHomeDir != null) {
        File maybeCassandra = Paths.get(dbHomeDir.getAbsolutePath(), "bin", dbCmd).toFile();
        if (maybeCassandra.exists() && maybeCassandra.canExecute()) dbCmdFile = maybeCassandra;
      }

      if (dbCmdFile == null)
        throw new IllegalArgumentException(
            String.format(
                "Unable to locate %s executable, set $%s or use --db-home", dbCmd, dbHomeEnv));

      // Verify Cassandra/DSE cmd works
      List<String> errorOutput = new ArrayList<>();
      String version =
          ShellUtils.executeWithHandlers(
              new ProcessBuilder(dbCmdFile.getAbsolutePath(), "-v"),
              (input, err) -> input.findFirst().orElse(null),
              (exitCode, err) -> {
                String s;
                errorOutput.add("'" + dbCmdFile.getAbsolutePath() + " -v' exit code: " + exitCode);
                while ((s = err.findFirst().orElse(null)) != null) errorOutput.add(s);
                return null;
              });

      if (version == null)
        throw new IllegalArgumentException(
            "Version check failed. stderr: " + String.join("\n", errorOutput));

      logger.info(
          String.format("%s Version %s", dbCmd.equals("dse") ? "DSE" : "Cassandra", version));
    } catch (IllegalArgumentException e) {
      logger.error("Error encountered:", e);
      logger.error(
          String.format(
              "Unable to start: unable to find or execute bin/%s",
              dbCmd, (db_home == null ? "use --db-home" : db_home)));
      System.exit(3);
    } catch (IOException io) {
      logger.error("Unknown error", io);
      System.exit(4);
    }
  }

  private boolean isDse() {
    try {
      // first check if dse cmd is already on the path
      if (UnixCmds.whichDse().isPresent()) {
        return true;
      }
    } catch (IOException e) {
      // nothing to do
    }

    if (db_home != null) {
      dbHomeDir = new File(db_home);
    } else if (System.getenv("DSE_HOME") != null) {
      dbHomeDir = new File(System.getenv("DSE_HOME"));
    }
    if (null == dbHomeDir) {
      return false;
    }

    File maybeDse = Paths.get(dbHomeDir.getAbsolutePath(), "bin", "dse").toFile();
    return maybeDse.exists() && maybeDse.canExecute();
  }

  void checkUnixSocket() {
    try {
      dbUnixSocketFile = Paths.get(db_unix_socket_file).toFile();
    } catch (InvalidPathException e) {
      logger.error(
          "Unable to start: db_unix_socket_file is not a valid file path: " + db_unix_socket_file);
      System.exit(3);
    }
  }

  void checkTLSDeps() {
    boolean hasAny = false;

    // CA CERT File Checks
    if (tls_ca_cert_file != null) {
      hasAny = true;
      if (!Files.exists(Paths.get(tls_ca_cert_file))) {
        logger.error("Specified CA Cert file does not exist: {}", tls_ca_cert_file);
        System.exit(10);
      }
      tlsCaCert = new File(tls_ca_cert_file);
    }

    // CERT File Checks
    if (tls_cert_file == null && hasAny) {
      logger.error("TLS Cert file is required when CA Cert flag is used");
      System.exit(11);
    }

    if (tls_cert_file != null && !hasAny) {
      logger.error("TLS CA Cert file is required when Cert flag is used");
      System.exit(12);
    }

    if (tls_cert_file != null) {
      hasAny = true;
      if (!Files.exists(Paths.get(tls_cert_file))) {
        logger.error("Specified Cert file does not exist: {}", tls_cert_file);
        System.exit(13);
      }
      tlsCert = new File(tls_cert_file);
    }

    // KEY File Checks
    if (tls_key_file == null && hasAny) {
      logger.error("TLS Key file is required when CA Cert flag is used");
      System.exit(14);
    }

    if (tls_key_file != null && !hasAny) {
      logger.error("TLS CA Key file is required when Cert flag is used");
      System.exit(15);
    }

    if (tls_key_file != null) {
      if (!Files.exists(Paths.get(tls_key_file))) {
        logger.error("Specified Key file does not exist: {}", tls_key_file);
        System.exit(16);
      }
      tlsKey = new File(tls_key_file);
    }

    useTls = hasAny;
  }

  void preflightChecks() {
    checkNettyDeps();
    checkTLSDeps();
    checkDbCmd();
    checkUnixSocket();
  }

  @VisibleForTesting
  void createSSLContext() throws SSLException {
    this.sslContext =
        SslContextBuilder.forServer(tlsCert, tlsKey)
            .trustManager(tlsCaCert)
            .clientAuth(ClientAuth.REQUIRE)
            .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
            .build();
  }

  @VisibleForTesting
  void createSSLWatcher() throws IOException {
    // Watch for tls_cert_file, tls_key_file and tls_ca_cert_file, add all their directories to
    // Filesystem Watcher
    WatchService watchService = FileSystems.getDefault().newWatchService();
    java.nio.file.Path tlsCertParent = tlsCert.toPath().getParent();
    java.nio.file.Path tlsKeyParent = tlsKey.toPath().getParent();
    java.nio.file.Path tlsCaCertParent = tlsCaCert.toPath().getParent();

    tlsCertParent.register(
        watchService,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY);
    tlsKeyParent.register(
        watchService,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY);
    tlsCaCertParent.register(
        watchService,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY);

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.execute(
        () -> {
          while (true) {
            try {
              WatchKey key = watchService.take();
              List<WatchEvent<?>> events = key.pollEvents();
              boolean reloadNeeded = false;
              for (WatchEvent<?> event : events) {
                WatchEvent.Kind<?> kind = event.kind();

                WatchEvent<java.nio.file.Path> ev = (WatchEvent<java.nio.file.Path>) event;
                java.nio.file.Path eventFilename = ev.context();

                if (tlsCertParent.resolve(eventFilename).equals(tlsCert.toPath())
                    || tlsKeyParent.resolve(eventFilename).equals(tlsKey.toPath())
                    || tlsCaCertParent.resolve(eventFilename).equals(tlsCaCert)) {
                  // Something in the TLS has been modified.. recreate SslContext
                  reloadNeeded = true;
                }
              }
              if (!key.reset()) {
                // The watched directories have disappeared..
                break;
              }
              if (reloadNeeded) {
                logger.info("Detected change in the SSL/TLS certificates, reloading.");
                createSSLContext();
              }
            } catch (InterruptedException e) {
              // Do something.. just log?
              logger.error("Filesystem watcher received InterruptedException", e);
            } catch (IOException e) {
              logger.error("Filesystem watcher received IOException", e);
            }
          }
        });
  }

  private NettyJaxrsServer startHTTPService(String hostname, int port) throws IOException {
    NettyJaxrsServer server;

    if (useTls) {
      createSSLContext();
      createSSLWatcher();
      server = new NettyJaxrsTLSServer(sslContext);
    } else {
      server = new NettyJaxrsServer();
      server.setIoWorkerCount(2);
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

  private NettyJaxrsServer startHTTPService(File socketFile) {
    EventLoopGroup loopGroup =
        PlatformDependent.isOsx() ? new KQueueEventLoopGroup(2) : new EpollEventLoopGroup(2);

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
    try {
      ParseResult<Cli> result = parser.parseWithResult(args);

      if (result.wasSuccessful()) {
        // Parsed successfully, so just run the command and exit
        result.getCommand().run();
      } else {
        // Parsing failed
        // Display errors and then the help information
        System.err.println(String.format("%d errors encountered:", result.getErrors().size()));
        int i = 1;
        for (ParseException e : result.getErrors()) {
          System.err.println(String.format("Error %d: %s", i, e.getMessage()));
          i++;
        }

        System.err.println();

        result.getCommand().help.showHelp();
      }
    } catch (ParseException p) {
      //noinspection StatementWithEmptyBody
      if (args.length > 0 && (args[0].equals("-h") || args[0].equals("--help"))) {
        // don't tell the user it's a usage error
        // a bug in airline (https://github.com/airlift/airline/issues/44) prints a usage error even
        // if
        // a user just uses -h/--help
      } else {
        System.err.println(String.format("Usage error: %s", p.getMessage()));
        System.err.println();
      }

      try {
        Help.help(parser.getCommandMetadata(), System.err);
      } catch (IOException e) {
        e.printStackTrace();
      }
    } catch (Exception e) {
      // Errors should be being collected so if anything is thrown it is unexpected
      System.err.println(String.format("Unexpected error: %s", e.getMessage()));
      e.printStackTrace(System.err);
    }
  }

  List<ChannelHandler> accessLogHandlers(String protocol) {
    @ChannelHandler.Sharable
    class AccessLogInbound extends ChannelInboundHandlerAdapter {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
          HttpRequest r = (HttpRequest) msg;
          ctx.channel()
              .attr(AttributeKey.valueOf("URIINFO"))
              .set(NettyUtil.extractUriInfo(r, "", protocol).getPath());
        }

        super.channelRead(ctx, msg);
      }
    }

    @ChannelHandler.Sharable
    class AccessLogOutbound extends ChannelOutboundHandlerAdapter {
      @Override
      public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
          throws Exception {
        if (msg instanceof HttpResponse) {
          HttpResponse r = (HttpResponse) msg;
          SocketAddress addr = ctx.channel().remoteAddress();
          logger.info(
              "address={} url={} status={}",
              addr == null ? protocol : addr,
              ctx.channel().attr(AttributeKey.valueOf("URIINFO")).get(),
              r.getStatus());
        }

        super.write(ctx, msg, promise);
      }
    }

    return ImmutableList.of(new AccessLogInbound(), new AccessLogOutbound());
  }
}
