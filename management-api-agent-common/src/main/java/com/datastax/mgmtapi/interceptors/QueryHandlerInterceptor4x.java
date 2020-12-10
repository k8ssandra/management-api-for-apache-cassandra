/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.interceptors;

import java.util.concurrent.Callable;
import java.util.regex.Matcher;

import com.datastax.mgmtapi.ShimLoader;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.cql3.QueryHandler;
import org.apache.cassandra.service.QueryState;

import static com.datastax.mgmtapi.interceptors.QueryHandlerInterceptor.handlePrefix;
import static com.datastax.mgmtapi.interceptors.QueryHandlerInterceptor.opsPattern;

public class QueryHandlerInterceptor4x
{
    public static ElementMatcher<? super TypeDescription> type()
    {
        return ElementMatchers.isSubTypeOf(QueryHandler.class);
    }

    public static AgentBuilder.Transformer transformer()
    {
        return new AgentBuilder.Transformer()
        {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule)
            {
                return builder.method(ElementMatchers.named("parse")).intercept(MethodDelegation.to(QueryHandlerInterceptor4x.class));
            }
        };
    }

    @RuntimeType
    public static Object intercept(@AllArguments Object[] allArguments, @SuperCall Callable<Object> zuper) throws Throwable
    {
        if (allArguments.length > 0 && allArguments[0] != null && allArguments[0] instanceof String)
        {
            String query = (String) allArguments[0];
            if (query.startsWith(handlePrefix))
            {
                QueryState state = (QueryState) allArguments[1];

                if (state.getClientState().isInternal)
                {
                    Matcher m = opsPattern.matcher(query);
                    if (m.matches())
                    {
                        return ShimLoader.instance.get().makeRpcStatement(m.group(1), m.group(2).trim().isEmpty() ? new String[]{} : m.group(2).split("\\s*,\\s*"));
                    }
                }
            }
        }

        return zuper.call();
    }
}
