/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package org.apache.cassandra.locator;

import com.datastax.mgmtapi.ShimLoader;
import java.util.List;
import java.util.Map;

public class K8SeedProvider implements SeedProvider {

  public K8SeedProvider(Map<String, String> m) {}

  @Override
  public List getSeeds() {
    return ShimLoader.instance.get().getK8SeedProvider().getSeeds();
  }
}
