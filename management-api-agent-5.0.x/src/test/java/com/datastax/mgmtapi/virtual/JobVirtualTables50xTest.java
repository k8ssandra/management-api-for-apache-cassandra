/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.virtual;

import com.datastax.mgmtapi.shim.CassandraAPI50x;
import com.datastax.mgmtapi.shims.CassandraAPI;

public class JobVirtualTables50xTest extends JobVirtualTablesTestBase {
  @Override
  protected CassandraAPI createApi() {
    return new CassandraAPI50x();
  }
}
