/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.shim.DseAPI69;
import com.datastax.mgmtapi.shims.CassandraAPI;

public class CassandraAPIServiceProviderDse69 implements CassandraAPIServiceProvider {

  @Override
  public CassandraAPI getCassandraAPI() {
    return new DseAPI69();
  }
}
