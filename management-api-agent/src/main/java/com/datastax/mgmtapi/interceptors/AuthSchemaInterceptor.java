package com.datastax.mgmtapi.interceptors;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.cassandra.cql3.QueryHandler;

public class AuthSchemaInterceptor
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
                return builder.method(ElementMatchers.named("process")).intercept(MethodDelegation.to(QueryHandlerInterceptor.class));
            }
        };
    }



}
