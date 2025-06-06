/*
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
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8SeedProvider51x implements SeedProvider {
  private static final Logger logger = LoggerFactory.getLogger(K8SeedProvider51x.class);

  public K8SeedProvider51x() {}

  public List<InetAddressAndPort> getSeeds() {
    Config conf;
    try {
      conf = DatabaseDescriptor.loadConfig();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    String[] hosts = conf.seed_provider.parameters.get("seeds").split(",", -1);
    List<InetAddressAndPort> seeds = new ArrayList<>(hosts.length);
    for (String host : hosts) {
      try {
        /**
         * A name may resolve to multiple seed node IPs, as would be the case in Kubernetes when a
         * headless service is used to represent the seed nodes in a cluster, which is why we use
         * `getAllByName` here instead of `getByName`.
         */
        seeds.addAll(
            Arrays.asList(InetAddress.getAllByName(host.trim())).stream()
                .map(n -> InetAddressAndPort.getByAddress(n))
                .collect(Collectors.toList()));
      } catch (UnknownHostException ex) {
        // not fatal... DD will bark if there end up being zero seeds.
        logger.warn("Seed provider couldn't lookup host {}", host);
      }
    }
    return Collections.unmodifiableList(seeds);
  }
}
