/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.interceptors;

import com.datastax.mgmtapi.NodeOpsProvider;
import com.datastax.mgmtapi.ShimLoader;
import com.datastax.mgmtapi.ipc.IPCController;
import com.google.common.collect.ImmutableMap;
import io.k8ssandra.metrics.interceptors.MetricsInterceptor;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.cassandra.transport.CBUtil;
import org.apache.cassandra.transport.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraDaemonInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(CassandraDaemonInterceptor.class);
  private static final String socketFileStr = System.getProperty("db.unix_socket_file");

  public static ElementMatcher<? super TypeDescription> type() {
    return ElementMatchers.nameEndsWith(".CassandraDaemon");
  }

  public static Transformer transformer() {
    return (builder, typeDescription, classLoader, module, protectionDomain) ->
        builder
            .method(ElementMatchers.named("start"))
            .intercept(
                MethodDelegation.to(CassandraDaemonInterceptor.class)
                    .andThen(MethodDelegation.to(MetricsInterceptor.class))
                    .andThen(SuperMethodCall.INSTANCE));
  }

  public static void intercept(@SuperCall Callable<Void> zuper) throws Exception {
    try {
      logger.info("Starting DataStax Management API Agent for Apache Cassandra v0.1");

      NodeOpsProvider.instance.get().register();

      if (!Epoll.isAvailable()) throw new RuntimeException("Event loop needed");

      File unixSock = Paths.get(socketFileStr).toFile();

      final EventLoopGroup group = new EpollEventLoopGroup(8);
      final Server.ConnectionTracker connectionTracker = new Server.ConnectionTracker();

      IPCController controller =
          IPCController.newServer()
              .withEventLoop(group)
              .withSocketFile(unixSock)
              .withChannelHandler(
                  ShimLoader.instance.get().makeSocketInitializer(connectionTracker))
              .withChannelOptions(
                  ImmutableMap.of(
                      ChannelOption.ALLOCATOR, CBUtil.allocator,
                      ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 32 * 1024,
                      ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 8 * 1024))
              .build();

      controller.start();

      logger.info("Starting listening for CQL clients on {} ...", socketFileStr);

      connectionTracker.allChannels.add(
          controller
              .channel()
              .orElseThrow(() -> new RuntimeException("Unix Socket Channel missing")));

      // Hook into things that have hooks
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    controller.stop();
                    NodeOpsProvider.instance.get().unregister();
                  }));
    } catch (Exception e) {
      logger.warn("Problem starting DataStax Management API for Apache Cassandra", e);
    }
  }
}
