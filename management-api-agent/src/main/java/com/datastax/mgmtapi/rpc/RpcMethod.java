/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.ResultSet;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.serializers.TypeSerializer;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.transport.messages.ResultMessage;


public class RpcMethod
{
    private static final Logger logger = LoggerFactory.getLogger(RpcMethod.class);
    private final Method method;
    private final RpcObject rpcObject;
    private final String name;
    private final List<TypeSerializer> argSerializers;
    private final List<AbstractType> argTypes;
    private final List<String> argNames;
    private final ObjectSerializer retSerializer;
    private final OptionalInt clientStateArgIdx;
    private final List<Pair<Integer, RpcParam>> params;

    <R> RpcMethod(Method method, RpcObject rpcObject)
    {
        this.method = method;
        this.rpcObject = rpcObject;
        this.name = method.getAnnotation(Rpc.class).name();

        Annotation[][] allAnnotations = method.getParameterAnnotations();
        params = IntStream.range(0, method.getParameterCount()).boxed()
                .flatMap(argIdx -> Arrays.stream(allAnnotations[argIdx])
                        .filter(a -> a instanceof RpcParam)
                        .findFirst()
                        .map(RpcParam.class::cast)
                        .map(rpcParam -> Stream.of(Pair.of(argIdx, rpcParam)))
                        .orElseGet(Stream::empty))
                .collect(Collectors.toList());

        Class<?>[] paramTypes = method.getParameterTypes();
        clientStateArgIdx = IntStream.range(0, method.getParameterCount())
                .filter(argIdx -> paramTypes[argIdx] == RpcClientState.class)
                .findFirst();

        int expectedParamsCount = params.size() + (clientStateArgIdx.isPresent() ? 1 : 0);
        if (method.getParameterCount() != expectedParamsCount)
        {
            throw new AssertionError(String.format(
                    "All arguments for %s.%s must be annotated with either RpcParam or RpcClientState",
                    rpcObject.getName(),
                    name));
        }

        Type[] genericParamTypes = method.getGenericParameterTypes();
        this.argSerializers = params.stream()
                .map(p -> GenericSerializer.getSerializer(genericParamTypes[p.getKey()]))
                .collect(Collectors.toList());

        this.argTypes = params.stream()
                .map(p -> GenericSerializer.getTypeOrException(genericParamTypes[p.getKey()]))
                .collect(Collectors.toList());

        this.argNames = params.stream()
                .map(p -> p.getValue().name())
                .collect(Collectors.toList());

        if (method.getAnnotation(Rpc.class).multiRow())
        {
            Preconditions.checkArgument(Collection.class.isAssignableFrom(method.getReturnType()),
                    "If mutli-row result set is requested, the method return type must be an implementation of java.util.Collection");
            Type elemType = ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
            Preconditions.checkArgument(elemType instanceof Class<?>,
                    "If multi-row result set is request, the element type must be a Class");
            this.retSerializer = new ObjectSerializer<>((Class<?>) elemType);
        }
        else
        {
            this.retSerializer = new ObjectSerializer<>(method.getReturnType(), method.getGenericReturnType());
        }
    }

    public String getName()
    {
        return name;
    }

    public int getArgumentCount()
    {
        return argTypes.size();
    }

    public ColumnSpecification getArgumentSpecification(int position)
    {
        return new ColumnSpecification("system", rpcObject.getName()+"."+name, new ColumnIdentifier(argNames.get(position), false), argTypes.get(position));
    }

    public ResultMessage execute(ClientState clientState, List<ByteBuffer> parameters)
            throws RequestExecutionException
    {
        try
        {
            RpcClientState rpcClientState = RpcClientState.fromClientState(clientState);
            LazyRef<Object[]> rpcArgs = LazyRef.of(() -> getMethodArgs(rpcClientState, parameters));

            // endpoint is not explicitly provided or points to this node -> execute locally
            return toResultMessage(method.invoke(rpcObject.raw, rpcArgs.get()));
        }
        catch (Exception e)
        {
            throw createRpcExecutionException(e);
        }
    }

    private RpcExecutionException createRpcExecutionException(Throwable e)
    {
        String msg = String.format("Failed to execute method %s.%s", rpcObject.getName(), name);
        logger.info(msg, e);
        return RpcExecutionException.create(msg, e);
    }

    private Object[] getMethodArgs(RpcClientState rpcClientState, Collection<ByteBuffer> parameters)
    {
        Object[] args = new Object[method.getParameterCount()];
        clientStateArgIdx.ifPresent(idx -> args[idx] = rpcClientState);
        Object[] rpcParams = deserializeParameters(parameters);
        for (int i = 0; i < rpcParams.length; i++)
        {
            args[params.get(i).getKey()] = rpcParams[i];
        }
        return args;
    }

    public ResultSet toResultSet(Object object)
    {
        if (method.getAnnotation(Rpc.class).multiRow())
        {
            return retSerializer.toMultiRowResultSet((Collection) object, rpcObject.getName(), name);
        }
        else
        {
            return retSerializer.toResultSet(object, rpcObject.getName(), name);
        }
    }

    public ResultMessage toResultMessage(Object object)
    {
        if (object == null)
        {
            return new ResultMessage.Void();
        }
        else
        {
            return new ResultMessage.Rows(toResultSet(object));
        }
    }

    private Object[] deserializeParameters(Collection<ByteBuffer> args)
    {
        Object[] deserialized = new Object[args.size()];

        int i = 0;
        for (ByteBuffer arg : args)
        {
            deserialized[i] = arg != null ? argSerializers.get(i).deserialize(arg) : null;
            i++;
        }

        return deserialized;
    }
}
