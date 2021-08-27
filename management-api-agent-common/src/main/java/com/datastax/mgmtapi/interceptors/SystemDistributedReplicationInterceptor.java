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

import org.slf4j.Logger;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemDistributedReplicationInterceptor.class);
    private static final String SYSTEM_DISTRIBUTED_NTS_DC_OVERRIDE_PROPERTY = "cassandra.system_distributed_replication_dc_names";
    private static final String SYSTEM_DISTRIBUTED_NTS_RF_OVERRIDE_PROPERTY = "cassandra.system_distributed_replication_per_dc";
    /**
     * This DC_RF property is used to specify different RF per DC, instead of a single RF for all DCs
     * The format of the property value should be <dc1_name>:<dc1_rf>,<dc2_name>:<dc2_rf>,....
     *
     * ex.  cassandra.system_distributed_replication=dc1:1,dc2:3,dc3:3
     *
     * If both this override and either of the above overrides are present, this value will take
     * precedence.
     */
    private static final String SYSTEM_DISTRIBUTED_NTS_DC_RF_OVERRIDE_PROPERTY = "cassandra.system_distributed_replication";

    private static Map<String, String> parseDcRfOverrides()
    {
        Map<String, String> dcRfOverrides = null;
        try
        {
            if (System.getProperty(SYSTEM_DISTRIBUTED_NTS_DC_RF_OVERRIDE_PROPERTY) != null)
            {
                dcRfOverrides = new HashMap<>();
                String mappings = System.getProperty(SYSTEM_DISTRIBUTED_NTS_DC_RF_OVERRIDE_PROPERTY);
                for (String mapping : mappings.split(","))
                {
                    String map = mapping.trim();
                    List<String> parts = Arrays.stream(map.split(":"))
                            .map(String::trim)
                            .collect(Collectors.toList());
                    if (parts.size() != 2)
                    {
                        LOGGER.error("Invalid dc-rf mapping for {}: {}",
                                SYSTEM_DISTRIBUTED_NTS_DC_RF_OVERRIDE_PROPERTY,
                                mapping);
                    }
                    else {
                        String dc = parts.get(0);
                        Integer rf = Integer.parseInt(parts.get(1));
                        if (rf <= 0 || rf > 5)
                        {
                            LOGGER.error("Invalid repliction factor specified for {}: {}",
                                    SYSTEM_DISTRIBUTED_NTS_DC_RF_OVERRIDE_PROPERTY,
                                    mapping);
                        }
                        else
                        {
                            dcRfOverrides.put(dc, rf.toString());
                        }
                    }
                }
            }
        }
        catch (Throwable t)
        {
            LOGGER.error("Error parsing system distributed replication override properties", t);
        }

        return dcRfOverrides;
    }

    private static final Map<String, String> SYSTEM_DISTRIBUTED_NTS_OVERRIDE;
    static
    {
        Integer rfOverride = null;
        List<String> dcOverride = Collections.emptyList();
        Map<String, String> dcRfOverrides = null;
        Map<String, String> ntsOverride = new HashMap<>();
        ntsOverride.put(ReplicationParams.CLASS, NetworkTopologyStrategy.class.getSimpleName());

        try
        {
            dcRfOverrides = parseDcRfOverrides();
            rfOverride = Integer.getInteger(SYSTEM_DISTRIBUTED_NTS_RF_OVERRIDE_PROPERTY, null);
            dcOverride = Arrays.stream(System.getProperty(SYSTEM_DISTRIBUTED_NTS_DC_OVERRIDE_PROPERTY, "")
                    .split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
        catch (Throwable t)
        {
            LOGGER.error("Error parsing system distributed replication override properties", t);
        }

        if (dcRfOverrides != null && !dcRfOverrides.isEmpty())
        {
            ntsOverride.putAll(dcRfOverrides);
            LOGGER.info("Using override for distributed system keyspaces: {}", ntsOverride);
        }
        else if (rfOverride != null && !dcOverride.isEmpty())
        {
            //Validate reasonable defaults
            if (rfOverride <= 0 || rfOverride > 5)
            {
                LOGGER.error("Invalid value for {}", SYSTEM_DISTRIBUTED_NTS_RF_OVERRIDE_PROPERTY);
            }
            else
            {
                for (String dc : dcOverride)
                    ntsOverride.put(dc, String.valueOf(rfOverride));

                LOGGER.info("Using override for distributed system keyspaces: {}", ntsOverride);
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
