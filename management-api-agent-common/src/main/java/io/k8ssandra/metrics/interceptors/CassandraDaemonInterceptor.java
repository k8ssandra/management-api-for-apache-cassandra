package io.k8ssandra.metrics.interceptors;

import io.k8ssandra.metrics.builder.filter.CassandraMetricDefinitionFilter;
import io.k8ssandra.metrics.config.ConfigReader;
import io.k8ssandra.metrics.config.Configuration;
import io.k8ssandra.metrics.prometheus.CassandraDropwizardExports;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.cassandra.metrics.CassandraMetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class CassandraDaemonInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(CassandraDaemonInterceptor.class);

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith(".CassandraDaemon");
    }

    public static Transformer transformer()
    {
        return (builder, typeDescription, classLoader, module, protectionDomain) -> builder.method(ElementMatchers.named("start")).intercept(MethodDelegation.to(CassandraDaemonInterceptor.class));
    }

    public static void intercept(@SuperCall Callable<Void> zuper) throws Exception {
        zuper.call();
        logger.info("Starting Metric Collector for Apache Cassandra");

        // Read Configuration file
        Configuration config = ConfigReader.readConfig();

        // Initialize filtering
        CassandraMetricDefinitionFilter filter = new CassandraMetricDefinitionFilter(config.getFilters());

        // Add Cassandra metrics
        new CassandraDropwizardExports(CassandraMetricsRegistry.Metrics, filter).register();

        // Add JVM metrics
        DefaultExports.initialize();

        // Share them from HTTP server
//        final HTTPServer server = new HTTPServer.Builder()
//                .withPort(9104)
//                .build();
//
        logger.info("Metrics collector started");
//        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }
}
