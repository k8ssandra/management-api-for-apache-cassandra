/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.nio.ByteBuffer;
import java.util.List;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.service.ClientState;

public interface RpcMethod {

    String getName();

    public int getArgumentCount();

    public ColumnSpecification getArgumentSpecification(int i);

    public Object execute(ClientState state, List<ByteBuffer> parameters);
}
