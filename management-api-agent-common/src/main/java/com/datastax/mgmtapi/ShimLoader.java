/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.shims.CassandraAPI;
import com.datastax.mgmtapi.util.ServiceProviderLoader;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class ShimLoader {
  public static Supplier<CassandraAPI> instance = Suppliers.memoize(ShimLoader::loadShim);

  private static CassandraAPI loadShim() {
    ServiceProviderLoader<CassandraAPIServiceProvider> loader = new ServiceProviderLoader<>();
    CassandraAPIServiceProvider provider = loader.getProvider(CassandraAPIServiceProvider.class);
    return provider.getCassandraAPI();
  }
}
