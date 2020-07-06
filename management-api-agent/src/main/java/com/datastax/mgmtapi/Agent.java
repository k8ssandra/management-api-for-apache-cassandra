/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.interceptors.SystemDistributedReplicationInterceptor;
import com.datastax.mgmtapi.interceptors.CassandraDaemonInterceptor;
import com.datastax.mgmtapi.interceptors.CassandraRoleManagerInterceptor;
import com.datastax.mgmtapi.interceptors.QueryHandlerInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import org.apache.cassandra.gms.GossiperInterceptor;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

public class Agent {

    public static void premain(String arg, Instrumentation inst) throws Exception {

        new AgentBuilder.Default()
                //.disableClassFormatChanges()
                //.with(AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly()) //For debug
                .ignore(new AgentBuilder.RawMatcher.ForElementMatchers(nameStartsWith("net.bytebuddy.").or(isSynthetic()), any(), any()))
                //Cassandra Daemon
                .type(CassandraDaemonInterceptor.type())
                .transform(CassandraDaemonInterceptor.transformer())
                //Query Handler
                .type(QueryHandlerInterceptor.type())
                .transform(QueryHandlerInterceptor.transformer())
                //Seed Reload support
                .type(GossiperInterceptor.type())
                .transform(GossiperInterceptor.transformer())
                //Auth Setup
                .type(CassandraRoleManagerInterceptor.type())
                .transform(CassandraRoleManagerInterceptor.transformer())
                .type(SystemDistributedReplicationInterceptor.type())
                .transform(SystemDistributedReplicationInterceptor.transformer())
                .installOn(inst);
    }
}