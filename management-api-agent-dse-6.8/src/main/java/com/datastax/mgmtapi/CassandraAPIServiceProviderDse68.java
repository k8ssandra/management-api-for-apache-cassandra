/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.shim.DseAPI68;
import com.datastax.mgmtapi.shims.CassandraAPI;

public class CassandraAPIServiceProviderDse68 implements CassandraAPIServiceProvider {

  @Override
  public CassandraAPI getCassandraAPI() {
    return new DseAPI68();
  }

}
