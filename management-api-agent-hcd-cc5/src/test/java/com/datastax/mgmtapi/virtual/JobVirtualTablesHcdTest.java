/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.virtual;

import com.datastax.mgmtapi.shim.CassandraAPIHcd;
import com.datastax.mgmtapi.shims.CassandraAPI;

public class JobVirtualTablesHcdTest extends JobVirtualTablesTestBase {
  @Override
  protected CassandraAPI createApi() {
    return new CassandraAPIHcd();
  }
}
