/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.interceptors;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.NodeOpsProvider;
import com.datastax.mgmtapi.ShimLoader;
import com.datastax.mgmtapi.rpc.RpcMethod;
import com.datastax.mgmtapi.rpc.RpcRegistry;
import com.datastax.mgmtapi.shims.RpcStatementShim;
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
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.QueryHandler;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.QueryState;

public class QueryHandlerInterceptor
{
    private static final Logger logger = LoggerFactory.getLogger(QueryHandlerInterceptor.class);
    static final String handlePrefix = "CALL " + NodeOpsProvider.RPC_CLASS_NAME + ".";
    static final Pattern opsPattern = Pattern.compile("^CALL NodeOps\\.([^\\(]+)\\(([^\\)]*)\\)");

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

    @RuntimeType
    public static Object intercept(@AllArguments Object[] allArguments, @SuperCall Callable<Object> zuper) throws Throwable
    {
        if (allArguments.length > 0 && allArguments[0] != null)
        {
            if (allArguments[0] instanceof String)
            {
                String query = (String) allArguments[0];
                if (query.startsWith(handlePrefix))
                {
                    QueryState state = (QueryState) allArguments[1];
                    QueryOptions options = (QueryOptions) allArguments[2];

                    if (state.getClientState().isInternal)
                    {
                        Matcher m = opsPattern.matcher(query);
                        if (m.matches())
                        {
                            return handle(state.getClientState(), options, NodeOpsProvider.RPC_CLASS_NAME, m.group(1), m.group(2).trim().isEmpty() ? new String[]{} : m.group(2).split("\\s*,\\s*"));
                        }
                    }
                }
            }
            else if (allArguments[0] instanceof RpcStatementShim)
            {
                RpcStatementShim statement = (RpcStatementShim) allArguments[0];
                QueryState state = (QueryState) allArguments[1];
                QueryOptions options = (QueryOptions) allArguments[2];

                return handle(state.getClientState(), options, NodeOpsProvider.RPC_CLASS_NAME, statement.getMethod(), statement.getParams());
            }
        }


        return zuper.call();
    }

    private static Object handle(ClientState state, QueryOptions options, String object, String method, String[] params) throws Exception
    {
        Optional<RpcMethod> rpcMethod = RpcRegistry.lookupMethod(object, method);

        if (!rpcMethod.isPresent())
        {
            throw new InvalidRequestException(String.format("Missing method: %s.%s", object, method));
        }

        if (rpcMethod.get().getArgumentCount() != params.length)
        {
            throw new InvalidRequestException(String.format("Incorrect number of arguments received for method %s.%s", object, method));
        }

        List<ByteBuffer> parameters = new ArrayList<>();
        int argumentIndex = 0;

        logger.trace("RPC CALL {} with {} args", method, rpcMethod.get().getArgumentCount());

        for (String value : params)
        {
            logger.trace("Arg {} = '{}'", argumentIndex, value);
            ColumnSpecification spec = rpcMethod.get().getArgumentSpecification(argumentIndex++);
            if (value.equals("?"))
                parameters.add(options.getValues().get(argumentIndex - 1));
            else
                parameters.add(spec.type.fromString(value));
        }

        return ShimLoader.instance.get().handleRpcResult(() -> rpcMethod.get().execute(state, parameters));
    }
}
