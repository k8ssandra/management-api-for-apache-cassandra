/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.shim.CassandraAPI4x;
import com.datastax.mgmtapi.shims.CassandraAPI;

public class CassandraAPIServiceProvider4x implements CassandraAPIServiceProvider {

  @Override
  public CassandraAPI getCassandraAPI() {
    return new CassandraAPI4x();
  }

}
