/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.lang.reflect.Method;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.shim.CassandraAPI3x;
import com.datastax.mgmtapi.shim.CassandraAPI4x;
import com.datastax.mgmtapi.shims.CassandraAPI;
import org.apache.cassandra.service.StorageService;

public class ShimLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeOpsProvider.class);
    public static final Supplier<CassandraAPI> instance = Suppliers.memoize(ShimLoader::loadShim);

    private static CassandraAPI loadShim()
    {
        String version = StorageService.instance.getReleaseVersion();
        Optional<CassandraAPI> dseAPI = maybeGetDseAPI();
        if (dseAPI.isPresent())
        {
            return dseAPI.get();
        }

        if (version.startsWith("3."))
            return new CassandraAPI3x();

        if (version.startsWith("4."))
            return new CassandraAPI4x();

        throw new RuntimeException("No Cassandra API Shim found for version: " + version);
    }

    private static Optional<CassandraAPI> maybeGetDseAPI()
    {
        String dseVersion = "UNKNOWN";
        try
        {
            Method getDSEReleaseVersion = StorageService.instance.getClass().getDeclaredMethod("getDSEReleaseVersion");
            dseVersion = (String) getDSEReleaseVersion.invoke(StorageService.instance);
            if (dseVersion.startsWith("6.8"))
            {
                // so that we don't have a direct dependency to the DSE shim project
                return Optional.of((CassandraAPI) Class.forName("com.datastax.mgmtapi.shim.DseAPI68").getConstructor().newInstance());
            }
        }
        catch (Exception e)
        {
            LOGGER.warn(String.format("No DSE API Shim found for DSE Version %s. Error was: %s", dseVersion, e.getMessage()), e);
        }
        return Optional.empty();
    }
}
