/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.virtual;

import com.datastax.mgmtapi.shim.CassandraAPI60x;
import com.datastax.mgmtapi.shims.CassandraAPI;

public class JobVirtualTables60xTest extends JobVirtualTablesTestBase {
  @Override
  protected CassandraAPI createApi() {
    return new CassandraAPI60x();
  }
}
