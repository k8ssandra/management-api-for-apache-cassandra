/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.k8ssandra.metrics.interceptors;

import io.k8ssandra.metrics.config.ConfigReader;
import io.k8ssandra.metrics.config.Configuration;
import io.k8ssandra.metrics.http.NettyMetricsHttpServer;
import io.k8ssandra.metrics.prometheus.CassandraDropwizardExports;
import io.k8ssandra.metrics.prometheus.CassandraTasksExports;
import io.k8ssandra.metrics.prometheus.JvmExports;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import java.util.concurrent.Callable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.cassandra.metrics.CassandraMetricsRegistry;
import org.apache.cassandra.utils.CassandraVersion;
import org.apache.cassandra.utils.FBUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(MetricsInterceptor.class);

  public static ElementMatcher<? super TypeDescription> type() {
    return ElementMatchers.nameEndsWith(".CassandraDaemon");
  }

  public static Transformer transformer() {
    return (builder, typeDescription, classLoader, module, protectionDomain) ->
        builder
            .method(ElementMatchers.named("start"))
            .intercept(MethodDelegation.to(MetricsInterceptor.class));
  }

  public static void intercept(@SuperCall Callable<Void> zuper) throws Exception {
    try {
      CassandraVersion cassandraVersion =
          new CassandraVersion(FBUtilities.getReleaseVersionString());

      CassandraVersion minCassandraVersion;
      if (cassandraVersion.major < 4) {
        minCassandraVersion = new CassandraVersion("3.11.13");
      } else {
        minCassandraVersion = new CassandraVersion("4.0.4");
      }

      if (minCassandraVersion.compareTo(cassandraVersion) > 0) {
        logger.error(
            "/metrics endpoint is not supported in versions older than {}", minCassandraVersion);
        return;
      }
    } catch (java.lang.NoClassDefFoundError expected) {
      // We're dealing with DSE here and can safely ignore this
    }

    try {
      logger.info("Starting Metric Collector for Apache Cassandra");

      // Read Configuration file
      Configuration config = ConfigReader.readConfig();

      // Add Cassandra metrics
      new CassandraDropwizardExports(CassandraMetricsRegistry.Metrics, config).register();

      // Add JVM metrics
      new JvmExports(config).register();

      // Add task metrics
      if (!config.isExtendedDisabled()) {
        new CassandraTasksExports(CassandraMetricsRegistry.Metrics, config).register();
      }

      // Create /metrics handler. Note, this doesn't support larger than nThreads=1
      final EventLoopGroup httpGroup = new EpollEventLoopGroup(1);

      // Share them from HTTP server
      NettyMetricsHttpServer server = new NettyMetricsHttpServer(config);
      server.start(httpGroup);

      logger.info("Metrics collector started");

      Runtime.getRuntime().addShutdownHook(new Thread(httpGroup::shutdownGracefully));
    } catch (Throwable t) {
      logger.error("Unable to start metrics endpoint", t);
    }
  }
}
