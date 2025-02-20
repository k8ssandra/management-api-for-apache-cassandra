/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.shim;

import com.datastax.mgmtapi.shims.RpcStatementShim;
import org.apache.cassandra.audit.AuditLogContext;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage;

public class RpcStatement implements RpcStatementShim {
  private final String method;
  private final String[] params;

  public RpcStatement(String method, String[] params) {
    this.method = method;
    this.params = params;
  }

  @Override
  public void authorize(ClientState clientState) {}

  /**
   * as of Cassandra 4.1.6, CASSANDRA-19534, org.apache.cassandra.cql3.CQLStatement no longer has
   * the method signature below, so we need to remove the @Override annotation. For Cassandra 5.0,
   * this fix was ported between 5.0-rc1 and 5.0-rc2.
   */
  public ResultMessage execute(QueryState queryState, QueryOptions queryOptions, long l) {
    return new ResultMessage.Void();
  }

  @Override
  public ResultMessage executeLocally(QueryState queryState, QueryOptions queryOptions) {
    return new ResultMessage.Void();
  }

  @Override
  public AuditLogContext getAuditLogContext() {
    return null;
  }

  @Override
  public String getMethod() {
    return method;
  }

  @Override
  public String[] getParams() {
    return params;
  }

  @Override
  public void validate(ClientState cs) {}

  @Override
  public String getRawCQLStatement() {
    throw new UnsupportedOperationException("Not supported yet."); // Generated from
    // nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
  }
}
