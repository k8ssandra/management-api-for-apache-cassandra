/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.cassandra.auth.Permission;

/**
 * Keeps track of RPC objects, which in turn keep track of their methods, which handle the actual serialization.
 * Also contains the parsing code, although that should be fixed at some point.
 */
public class RpcRegistry
{
    protected static final ConcurrentHashMap<String, RpcObject> objects = new ConcurrentHashMap<>();

    /**
     * For statics, just use any object of the right type.  Method.invoke() will simply ignore the object parameter.
     */
    public static void register(Object o)
    {
        register(o.getClass().getSimpleName(), o);
    }

    public static void register(String name, Object o)
    {
        if (objects.putIfAbsent(name, new RpcObject(name, o)) != null)
        {
            throw new AssertionError("Multiple assignments to " + name + "!");
        }
    }

    public static boolean unregister(String name)
    {
        return null != objects.remove(name);
    }

    public static Optional<RpcMethod> lookupMethod(String object, String method)
    {
        if (objects.containsKey(object))
        {
            return objects.get(object).lookupMethod(method);
        }
        return Optional.empty();
    }

    public static boolean objectExists(String object)
    {
        return objects.containsKey(object);
    }

    public static boolean methodExists(String object, String method)
    {
        return lookupMethod(object, method).isPresent();
    }

    public static Set<Permission> getAllPermissions()
    {
        Set<Permission> permissions = new HashSet<>();
        for (RpcObject rpcObject : objects.values())
        {
            for (RpcMethod rpcMethod : rpcObject.getMethods())
            {
                addRpcMethodPerm(permissions, rpcMethod);
            }
        }
        return permissions;
    }

    public static Set<Permission> getObjectPermissions(String object)
    {
        Set<Permission> permissions = new HashSet<>();
        if (objects.containsKey(object))
        {
            RpcObject rpcObject = objects.get(object);
            for (RpcMethod rpcMethod : rpcObject.getMethods())
            {
                addRpcMethodPerm(permissions, rpcMethod);
            }
        }
        return permissions;
    }

    public static Set<Permission> getMethodPermissions(String object, String method)
    {
        Set<Permission> permissions = new HashSet<>();
        if (objects.containsKey(object))
        {
            RpcMethod rpcMethod = objects.get(object).getMethod(method);
            if (rpcMethod != null)
            {
                addRpcMethodPerm(permissions, rpcMethod);
            }
        }
        return permissions;
    }

    private static void addRpcMethodPerm(Set<Permission> permissions, RpcMethod rpcMethod)
    {
    }
}
