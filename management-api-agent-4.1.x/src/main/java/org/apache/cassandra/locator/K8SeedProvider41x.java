/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package org.apache.cassandra.locator;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.datastax.mgmtapi.ShimLoader;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.DiscoveryV1Api;
import io.kubernetes.client.openapi.models.V1Endpoint;
import io.kubernetes.client.openapi.models.V1EndpointConditions;
import io.kubernetes.client.openapi.models.V1EndpointSlice;
import io.kubernetes.client.util.Namespaces;
import io.kubernetes.client.util.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;

public class K8SeedProvider41x implements SeedProvider
{
    private static final Logger logger = LoggerFactory.getLogger(K8SeedProvider41x.class);

    private static final int MINIMUM_ENDPOINTSLICE_VERSION = 21;

    public K8SeedProvider41x() {
    }

    public List<InetAddressAndPort> getSeeds()
    {
        try {
            org.apache.cassandra.config.Config conf = DatabaseDescriptor.loadConfig();
            ApiClient client = io.kubernetes.client.util.Config.defaultClient();
            Version version = new Version(client);
            int kubernetesVersion = Integer.parseInt(version.getVersion().getMinor());
            if(kubernetesVersion < MINIMUM_ENDPOINTSLICE_VERSION) {
                logger.info("Kubernetes server version is too old, using legacy method to get the seeds");
                return ShimLoader.instance.get().getK8SeedProvider().getSeeds();
            }

            Configuration.setDefaultApiClient(client);

            String[] hosts = conf.seed_provider.parameters.get("seeds").split(",", -1);
            DiscoveryV1Api discoveryApi = new DiscoveryV1Api(client);

            List<InetAddressAndPort> seeds = new ArrayList<>();
            for (String host : hosts) {
                V1EndpointSlice v1EndpointSlice = discoveryApi.readNamespacedEndpointSlice(host, Namespaces.getPodNamespace(), null);
                for (V1Endpoint endpoint : v1EndpointSlice.getEndpoints()) {
                    V1EndpointConditions conditions = endpoint.getConditions();
                    if (Boolean.FALSE.equals(conditions.getReady())) {
                        continue;
                    }
                    for (String address : endpoint.getAddresses()) {
                        try {
                            InetAddressAndPort inet = InetAddressAndPort.getByName(address);
                            seeds.add(inet);
                        } catch (UnknownHostException e) {
                            // This address simply isn't added
                        }
                    }
                }
            }

            return Collections.unmodifiableList(seeds);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
