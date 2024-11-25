/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.shim.CassandraAPIHcd;
import com.datastax.mgmtapi.shims.CassandraAPI;

public class CassandraAPIServiceProviderHcd implements CassandraAPIServiceProvider {

  @Override
  public CassandraAPI getCassandraAPI() {
    return new CassandraAPIHcd();
  }
}
