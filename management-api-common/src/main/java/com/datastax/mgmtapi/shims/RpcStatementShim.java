/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.shims;

import org.apache.cassandra.audit.AuditLogContext;
import org.apache.cassandra.audit.AuditLogEntryType;
import org.apache.cassandra.cql3.CQLStatement;

public interface RpcStatementShim extends CQLStatement {
  String getMethod();

  String[] getParams();

  @Override
  default AuditLogContext getAuditLogContext() {
    return new AuditLogContext(AuditLogEntryType.SELECT, "system", getMethod());
  }
}
