package org.apache.cassandra.locator;

import java.util.List;

import com.datastax.mgmtapi.ShimLoader;

public class K8SeedProvider implements SeedProvider
{
    @Override
    public List getSeeds()
    {
        return ShimLoader.instance.get().getK8SeedProvider().getSeeds();
    }
}
