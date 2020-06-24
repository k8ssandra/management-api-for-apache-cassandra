/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.interceptors;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.ShimLoader;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.auth.AuthKeyspace;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.exceptions.RequestExecutionException;

public class CassandraRoleManagerInterceptor
{
    private static final Boolean skipDefaultRoleSetup = Boolean.getBoolean("cassandra.skip_default_role_setup");
    private static final String DEFAULT_SUPERUSER_NAME = "cassandra";
    private static final String AUTH_KEYSPACE_NAME = "system_auth";
    private static final Logger logger = LoggerFactory.getLogger(CassandraRoleManagerInterceptor.class);

    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.nameEndsWith(".CassandraRoleManager");
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer()
        {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("setupDefaultRole")).intercept(MethodDelegation.to(CassandraRoleManagerInterceptor.class));
            }
        };
    }

    public static void intercept(@SuperCall Callable<Void> zuper) throws Exception
    {
        if (!skipDefaultRoleSetup)
        {
            zuper.call();
            return;
        }

        if (ShimLoader.instance.get().getStorageService().getTokenMetadata().sortedTokens().isEmpty())
            throw new IllegalStateException("CassandraRoleManager skipped default role setup: no known tokens in ring");

        try
        {
            if (!hasExistingRoles())
            {
                ShimLoader.instance.get().processQuery(String.format("INSERT INTO %s.%s (role, is_superuser, can_login, salted_hash) " +
                                "VALUES ('%s', false, false, '%s') USING TIMESTAMP 1",
                        AUTH_KEYSPACE_NAME,
                        AuthKeyspace.ROLES,
                        DEFAULT_SUPERUSER_NAME,
                        ""), ConsistencyLevel.QUORUM);
            }
        }
        catch (RequestExecutionException e)
        {
            logger.warn("CassandraRoleManager skipped default role setup: some nodes were not ready");
            throw e;
        }
    }

    private static boolean hasExistingRoles() throws RequestExecutionException
    {

        // Try looking up the 'cassandra' default role first, to avoid the range query if possible.
        String defaultSUQuery = String.format("SELECT * FROM %s.%s WHERE role = '%s'", AUTH_KEYSPACE_NAME, AuthKeyspace.ROLES, "cassandra");
        String allUsersQuery = String.format("SELECT * FROM %s.%s LIMIT 1", AUTH_KEYSPACE_NAME, AuthKeyspace.ROLES);
        return !ShimLoader.instance.get().processQuery(defaultSUQuery, ConsistencyLevel.ONE).isEmpty()
                || !ShimLoader.instance.get().processQuery(defaultSUQuery, ConsistencyLevel.QUORUM).isEmpty()
                || !ShimLoader.instance.get().processQuery(allUsersQuery, ConsistencyLevel.QUORUM).isEmpty();
    }

}
