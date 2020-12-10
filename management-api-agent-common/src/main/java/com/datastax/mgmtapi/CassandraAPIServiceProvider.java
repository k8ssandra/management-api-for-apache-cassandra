/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.shims.CassandraAPI;

public interface CassandraAPIServiceProvider
{
    CassandraAPI getCassandraAPI();
}
