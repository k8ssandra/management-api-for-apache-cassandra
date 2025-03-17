/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.shim.CassandraAPI51x;
import com.datastax.mgmtapi.shims.CassandraAPI;

public class CassandraAPIServiceProvider51x implements CassandraAPIServiceProvider {

  @Override
  public CassandraAPI getCassandraAPI() {
    return new CassandraAPI51x();
  }
}
