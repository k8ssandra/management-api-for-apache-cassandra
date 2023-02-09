/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.shim.CassandraAPI42x;
import com.datastax.mgmtapi.shims.CassandraAPI;

public class CassandraAPIServiceProvider42x implements CassandraAPIServiceProvider {

  @Override
  public CassandraAPI getCassandraAPI() {
    return new CassandraAPI42x();
  }
}
