/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;


import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;

public class RpcResource implements IResource
{
    enum Level
    {
        ROOT, OBJECT, METHOD
    }

    private static final String ROOT_NAME = "rpc";
    private static final RpcResource ROOT_RESOURCE = new RpcResource();
    private static final Set<Permission> DEFAULT_PERMISSIONS = ImmutableSet.of(Permission.AUTHORIZE);

    private final Level level;
    private final String object;
    private final String method;

    private RpcResource()
    {
        level = Level.ROOT;
        object = null;
        method = null;
    }

    private RpcResource(String object)
    {
        level = Level.OBJECT;
        this.object = object;
        this.method = null;
    }

    private RpcResource(String object, String method)
    {
        level = Level.METHOD;
        this.object = object;
        this.method = method;
    }

    /**
     * @return the root-level resource.
     */
    public static RpcResource root()
    {
        return ROOT_RESOURCE;
    }

    public static RpcResource object(String object)
    {
        return new RpcResource(object);
    }

    public static RpcResource method(String object, String method)
    {
        return new RpcResource(object, method);
    }

    /**
     * @return Printable name of the resource.
     */
    public String getName()
    {
        switch (level)
        {
            case ROOT:
                return ROOT_NAME;
            case OBJECT:
                return String.format("%s/%s", ROOT_NAME, object);
            case METHOD:
                return String.format("%s/%s/%s", ROOT_NAME, object, method);
        }
        throw new AssertionError();
    }

    /**
     * @return Parent of the resource, if any. Throws IllegalStateException if it's the root-level resource.
     */
    public IResource getParent()
    {
        switch (level)
        {
            case OBJECT:
                return root();
            case METHOD:
                return object(object);
        }
        throw new IllegalStateException("Root-level resource can't have a parent");
    }

    public boolean hasParent()
    {
        return level != Level.ROOT;
    }

    public boolean exists()
    {
        switch (level)
        {
            case ROOT:
                return true;
            case OBJECT:
                return RpcRegistry.objectExists(object);
            case METHOD:
                return RpcRegistry.methodExists(object, method);
        }
        throw new AssertionError();
    }

    public Set<Permission> applicablePermissions()
    {
        switch (level)
        {
            case ROOT:
                return Sets.union(DEFAULT_PERMISSIONS, RpcRegistry.getAllPermissions());
            case OBJECT:
                return Sets.union(DEFAULT_PERMISSIONS, RpcRegistry.getObjectPermissions(object));
            case METHOD:
                return Sets.union(DEFAULT_PERMISSIONS, RpcRegistry.getMethodPermissions(object, method));
        }
        throw new AssertionError();
    }

    @Override
    public String toString()
    {
        switch (level)
        {
            case ROOT:
                return "<all rpc>";
            case OBJECT:
                return String.format("<rpc object %s>", object);
            case METHOD:
                return String.format("<rpc method %s.%s>", object, method);
        }
        throw new AssertionError();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (!(o instanceof RpcResource))
            return false;

        RpcResource r = (RpcResource) o;

        return Objects.equal(level, r.level) &&
                Objects.equal(object, r.object) &&
                Objects.equal(method, r.method);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(level, object, method);
    }

}

