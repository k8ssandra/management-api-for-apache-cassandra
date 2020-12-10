/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import org.apache.cassandra.cql3.ResultSet;

public interface ObjectSerializer<T>
{
    /**
     * Serialize an object into a C* ResultSet, with each field as a named value.
     * @param obj  The object to serialize
     * @param ksName Pretend we are coming from this keyspace
     * @param cfName Pretend we are coming from this columnfamily
     */
    public ResultSet toResultSet(T obj, String ksName, String cfName);

    /**
     * Serialize an object into a C* multi-row ResultSet, with each field as a named value.
     *
     * @param obj The object to serialize
     * @param ksName Pretend we are coming from this keyspace
     * @param cfName Pretend we are coming from this columnfamily
     */
    public ResultSet toMultiRowResultSet(Collection<T> obj, String ksName, String cfName);

    public List<ByteBuffer> toByteBufferList(T obj);

    public ByteBuffer toByteBuffer(T obj);
}
