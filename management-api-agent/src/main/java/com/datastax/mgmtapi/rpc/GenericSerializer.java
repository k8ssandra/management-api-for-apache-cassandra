/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.BooleanType;
import org.apache.cassandra.db.marshal.ByteType;
import org.apache.cassandra.db.marshal.BytesType;
import org.apache.cassandra.db.marshal.DateType;
import org.apache.cassandra.db.marshal.DoubleType;
import org.apache.cassandra.db.marshal.EmptyType;
import org.apache.cassandra.db.marshal.FloatType;
import org.apache.cassandra.db.marshal.InetAddressType;
import org.apache.cassandra.db.marshal.Int32Type;
import org.apache.cassandra.db.marshal.ListType;
import org.apache.cassandra.db.marshal.LongType;
import org.apache.cassandra.db.marshal.MapType;
import org.apache.cassandra.db.marshal.SetType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.db.marshal.UUIDType;
import org.apache.cassandra.serializers.TypeSerializer;

/**
 * Uses reflection to look up an appropriate TypeSerializer/AbstractType to serialize objects
 * without writing annoying ByteBufferUtil.bytes(X/Y/Z) boilerplate.
 */
public class GenericSerializer
{
    // I considered using the drivers code (CodecRegistry, TypeCodec, etc) but decided that it made more sense to
    // use the server side stuff from Cassandra.

    // extending this (at least for the purpose of RPC calls) is relatively straightforward: write a class that
    // extends C*'s TypeSerializer and add to the map.  For actually getting data into C*'s UDTs it might be a bit
    // trickier.  Unfortunately, there is not always a direct 1:1 mapping between Java and Cassandra types.  A simple
    // example is millisecond timestamps, which could be serialized as 'long' and 'timestamp'.  The driver code
    // actually has some bounds for this, but I think for us it will be simpler to just write more serializers and
    // add them to the map.
    private static final ConcurrentHashMap<String, AbstractType> typeMap = new ConcurrentHashMap() {{
        put("void", EmptyType.instance);
        put("boolean", BooleanType.instance);
        put("java.lang.Boolean", BooleanType.instance);
        put("byte", ByteType.instance);
        put("java.lang.Byte", ByteType.instance);
        put("int", Int32Type.instance);
        put("java.lang.Integer", Int32Type.instance);
        put("long", LongType.instance);
        put("java.lang.Long", LongType.instance);
        put("float", FloatType.instance);
        put("java.lang.Float", FloatType.instance);
        put("double", DoubleType.instance);
        put("java.lang.Double", DoubleType.instance);
        put("java.lang.String", UTF8Type.instance);
        put("java.net.InetAddress", InetAddressType.instance);
        put("java.util.Date", DateType.instance);
        put("java.nio.ByteBuffer", BytesType.instance);
        put("java.util.UUID", UUIDType.instance);
    }};

    public static void registerType(String className, AbstractType<?> type)
    {
        if (typeMap.putIfAbsent(className, type) != null)
        {
            throw new IllegalStateException("The type " + className + " is already registered.");
        }
    }

    public static TypeSerializer getSerializer(Type type)
    {
        return getTypeOrException(type).getSerializer();
    }

    public static AbstractType getTypeOrException(Type type)
    {
        AbstractType ctype = getType(type);

        if (ctype == null)
        {
            throw new AssertionError(String.format("Add type '%s' to GenericSerializer", type.getTypeName()));
        }

        return ctype;
    }

    public static boolean simpleType(Type type)
    {
        return getType(type) != null;
    }

    /**
     * Most of the actual work is done here. Note that Generic type information is mostly destroyed at runtime
     * (a list is just a list).  For the Parameterized types to work correctly you have to call
     * Method.getGenericParameterTypes() or something similar.  Also, we currently punt on the frozen keyword.
     * @return The C* abstract type corresponding to the Java type, or null if not found/impossible.
     */
    public static AbstractType getType(Type type)
    {
        assert type != null;
        String strType = type.getTypeName();

        // Rather than hard coding List<Integer> List<Long> List<String> etc we create them as needed.  Also there
        // is no need for a lock as the actual serializers do that for us.
        if (!typeMap.containsKey(strType))
        {
            if (type instanceof ParameterizedType)
            {
                ParameterizedType ptype = (ParameterizedType) type;

                if (ptype.getRawType().getTypeName().equals("java.util.List"))
                {
                    assert ptype.getActualTypeArguments().length == 1;
                    typeMap.putIfAbsent(strType,
                                        ListType.getInstance(getType(ptype.getActualTypeArguments()[0]), false));
                }
                else if (ptype.getRawType().getTypeName().equals("java.util.Set"))
                {
                    assert ptype.getActualTypeArguments().length == 1;
                    typeMap.putIfAbsent(strType,
                                        SetType.getInstance(getType(ptype.getActualTypeArguments()[0]), false));
                }
                else if (ptype.getRawType().getTypeName().equals("java.util.Map"))
                {
                    assert ptype.getActualTypeArguments().length == 2;
                    typeMap.putIfAbsent(strType,
                                        MapType.getInstance(getType(ptype.getActualTypeArguments()[0]),
                                                            getType(ptype.getActualTypeArguments()[1]), false));
                }
                else
                {
                    throw new AssertionError("Don't know how to serialize generic type '" +
                                             ptype.getRawType().getTypeName() + "'");
                }
            }
            else
            {
                return null;
            }
        }

        return typeMap.get(strType);
    }
}
