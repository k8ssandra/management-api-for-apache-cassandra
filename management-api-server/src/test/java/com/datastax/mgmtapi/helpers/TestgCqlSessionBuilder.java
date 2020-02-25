/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

package com.datastax.mgmtapi.helpers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.NodeStateListener;
import com.datastax.oss.driver.api.core.metadata.schema.SchemaChangeListener;
import com.datastax.oss.driver.api.core.tracker.RequestTracker;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.internal.core.context.DefaultDriverContext;
import com.datastax.oss.driver.internal.core.context.DefaultNettyOptions;
import com.datastax.oss.driver.internal.core.context.NettyOptions;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.PromiseCombiner;

public class TestgCqlSessionBuilder extends CqlSessionBuilder
{
        @Override
        protected DriverContext buildContext(DriverConfigLoader configLoader, List<TypeCodec<?>> typeCodecs,
                NodeStateListener nodeStateListener, SchemaChangeListener schemaChangeListener, RequestTracker requestTracker,
                Map<String, String> localDatacenters, Map<String, Predicate<Node>> nodeFilters, ClassLoader classLoader)
        {
            return new TestDriverContext(configLoader, typeCodecs, nodeStateListener, schemaChangeListener,
                                         requestTracker, localDatacenters, nodeFilters, classLoader);
        }

    static class TestDriverContext extends DefaultDriverContext
    {
        TestDriverContext(DriverConfigLoader configLoader, List<TypeCodec<?>> typeCodecs,
                          NodeStateListener nodeStateListener, SchemaChangeListener schemaChangeListener, RequestTracker requestTracker,
                          Map<String, String> localDatacenters, Map<String, Predicate<Node>> nodeFilters, ClassLoader classLoader)
        {
            super(configLoader, typeCodecs, nodeStateListener, schemaChangeListener, requestTracker, localDatacenters, nodeFilters,
                  classLoader);
        }

        @Override
        protected NettyOptions buildNettyOptions()
        {
            return new DefaultNettyOptions(this)
            {
                @Override
                public Future<Void> onClose() {
                    DefaultPromise<Void> closeFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
                    GlobalEventExecutor.INSTANCE.execute(
                            () -> {
                                // default settings for java driver
                                int adminShutdownQuietPeriod = 0, ioShutdownQuietPeriod = 0;
                                int adminShutdownTimeout = 15, ioShutdownTimeout = 15;
                                TimeUnit adminShutdownUnit = TimeUnit.SECONDS, ioShutdownUnit = TimeUnit.SECONDS;

                                PromiseCombiner combiner = new PromiseCombiner();
                                combiner.add(
                                        adminEventExecutorGroup().shutdownGracefully(
                                                adminShutdownQuietPeriod, adminShutdownTimeout, adminShutdownUnit));
                                combiner.add(
                                        ioEventLoopGroup().shutdownGracefully(
                                                ioShutdownQuietPeriod, ioShutdownTimeout, ioShutdownUnit));
                                combiner.finish(closeFuture);
                            });
                    closeFuture.addListener(f -> getTimer().stop());
                    return closeFuture;
                }
            };
        }
    }
}
