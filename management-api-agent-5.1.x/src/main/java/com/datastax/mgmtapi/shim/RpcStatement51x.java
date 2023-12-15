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
import org.apache.cassandra.transport.Dispatcher;
import org.apache.cassandra.transport.messages.ResultMessage;

public class RpcStatement51x implements RpcStatementShim {
  private final String method;
  private final String[] params;

  public RpcStatement51x(String method, String[] params) {
    this.method = method;
    this.params = params;
  }

  @Override
  public void authorize(ClientState clientState) {}

  @Override
  public void validate(ClientState clientState) {}

  @Override
  public ResultMessage execute(
      QueryState queryState, QueryOptions queryOptions, Dispatcher.RequestTime rt) {
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
}
