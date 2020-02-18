/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

public class RpcObject
{
    protected final String name;
    protected final Object raw;
    protected final ImmutableMap<String, RpcMethod> rpcs;

    protected RpcObject(String name, Object object)
    {
        this.name = name;
        this.raw = object;
        Map<String, RpcMethod> found = new HashMap<>();

        // Note that getMethods() will only return public methods, which is for the best here anyway
        for (Method method : object.getClass().getMethods())
        {
            if (method.isAnnotationPresent(Rpc.class))
            {
                RpcMethod rpcMethod = new RpcMethod(method, this);
                if (found.containsKey(rpcMethod.getName()))
                {
                    throw new AssertionError(String.format("Naming conflict in class %s: method %s already exists. " +
                                                           "Cannot have two @Rpc annotated methods with the " +
                                                           "same name.",
                                                           object.getClass().getCanonicalName(), rpcMethod.getName()));
                }
                found.put(rpcMethod.getName(), rpcMethod);
            }
        }
        rpcs = ImmutableMap.copyOf(found);
    }


    protected String getName()
    {
        return name;
    }

    protected Optional<RpcMethod> lookupMethod(String name)
    {
        return Optional.ofNullable(rpcs.get(name));
    }

    protected Collection<RpcMethod> getMethods()
    {
        return rpcs.values();
    }

    protected RpcMethod getMethod(String name)
    {
        if (rpcs.containsKey(name))
        {
            return rpcs.get(name);
        }
        return null;
    }
}
