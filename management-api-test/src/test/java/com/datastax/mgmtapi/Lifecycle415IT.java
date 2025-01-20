/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.IOException;
import java.util.ArrayList;

public class Lifecycle415IT extends LifecycleIT {

  public Lifecycle415IT(String version) throws IOException {
    super(version);
  }
  /**
   * Override environment variables to specify the version of Cassandra to be used. For the test in
   * here, we want Cassandra 4.1.5 to test post-4.1.3 changes through pre-4.1.6 changes.
   *
   * @return A list of Docker build environment variables.
   */
  @Override
  protected ArrayList<String> getImageBuildArgs() {
    ArrayList<String> list = super.getImageBuildArgs();
    list.add("CASSANDRA_VERSION=4.1.5");
    return list;
  }
}
