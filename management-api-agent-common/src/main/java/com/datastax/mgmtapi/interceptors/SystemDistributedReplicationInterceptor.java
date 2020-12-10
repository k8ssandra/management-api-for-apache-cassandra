/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.interceptors;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;

import org.slf4j.LoggerFactory;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.locator.NetworkTopologyStrategy;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.KeyspaceParams;
import org.apache.cassandra.schema.ReplicationParams;

import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;

public class SystemDistributedReplicationInterceptor
{
    private static final String SYSTEM_DISTRIBUTED_NTS_DC_OVERRIDE_PROPERTY = "cassandra.system_distributed_replication_dc_names";
    private static final String SYSTEM_DISTRIBUTED_NTS_RF_OVERRIDE_PROPERTY = "cassandra.system_distributed_replication_per_dc";

    private static final Map<String, String> SYSTEM_DISTRIBUTED_NTS_OVERRIDE;
    static
    {
        Integer rfOverride = null;
        List<String> dcOverride = Collections.emptyList();
        Map<String, String> ntsOverride = new HashMap<>();
        ntsOverride.put(ReplicationParams.CLASS, NetworkTopologyStrategy.class.getSimpleName());

        try
        {
            rfOverride = Integer.getInteger(SYSTEM_DISTRIBUTED_NTS_RF_OVERRIDE_PROPERTY, null);
            dcOverride = Arrays.stream(System.getProperty(SYSTEM_DISTRIBUTED_NTS_DC_OVERRIDE_PROPERTY, "")
                    .split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
        catch (Throwable t)
        {
            LoggerFactory.getLogger(SystemDistributedReplicationInterceptor.class).error("Error parsing system distributed replication override properties", t);
        }

        if (rfOverride != null && !dcOverride.isEmpty())
        {
            //Validate reasonable defaults
            if (rfOverride <= 0 || rfOverride > 5)
            {
                LoggerFactory.getLogger(SystemDistributedReplicationInterceptor.class).error("Invalid value for {}", SYSTEM_DISTRIBUTED_NTS_RF_OVERRIDE_PROPERTY);
            }
            else
            {
                for (String dc : dcOverride)
                    ntsOverride.put(dc, String.valueOf(rfOverride));

                LoggerFactory.getLogger(SystemDistributedReplicationInterceptor.class).info("Using override for distributed system keyspaces: {}", ntsOverride);
            }
        }

        SYSTEM_DISTRIBUTED_NTS_OVERRIDE = ImmutableMap.copyOf(ntsOverride);
    }


    public static ElementMatcher<? super TypeDescription> type()
    {
        return nameEndsWith(".AuthKeyspace")
                .or(nameEndsWith(".TraceKeyspace"))
                .or(nameEndsWith(".SystemDistributedKeyspace"));
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer()
        {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("metadata")).intercept(MethodDelegation.to(SystemDistributedReplicationInterceptor.class));
            }
        };
    }

    public static KeyspaceMetadata intercept(@SuperCall Callable<KeyspaceMetadata> zuper) throws Exception
    {
        KeyspaceMetadata ksm = zuper.call();

        if (SYSTEM_DISTRIBUTED_NTS_OVERRIDE.size() > 1) //1 because we add class key
            return ksm.withSwapped(KeyspaceParams.create(true, SYSTEM_DISTRIBUTED_NTS_OVERRIDE));

        return ksm;
    }
}
