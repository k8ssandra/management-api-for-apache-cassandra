/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.shims;

import org.apache.cassandra.cql3.CQLStatement;

public interface RpcStatementShim extends CQLStatement
{
    String getMethod();
    String[] getParams();
}
