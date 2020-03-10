package com.datastax.mgmtapi;


import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import com.datastax.mgmtapi.shim.CassandraAPI3x;
import com.datastax.mgmtapi.shim.Cassandra4XAPI;
import com.datastax.mgmtapi.shims.CassandraAPI;
import org.apache.cassandra.service.StorageService;

public class ShimLoader
{
    public static final Supplier<CassandraAPI> instance = Suppliers.memoize(ShimLoader::loadShim);

    private static CassandraAPI loadShim()
    {
        String version = StorageService.instance.getReleaseVersion();

        if (version.startsWith("3."))
            return new CassandraAPI3x();

        if (version.startsWith("4."))
            return new Cassandra4XAPI();

        throw new RuntimeException("No Cassandra API Shim found for version: " + version);
    }
}
