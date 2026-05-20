/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.IOException;
import java.util.ArrayList;

public class Metrics502IT extends MetricsIT {

  public Metrics502IT(String version) throws IOException {
    super(version);
  }

  /**
   * Override environment variables to specify the version of Cassandra to be used. For the test in
   * here, we want Cassandra 5.0.2 to test Netty versions.
   *
   * @return A list of Docker build environment variables.
   */
  @Override
  protected ArrayList<String> getImageBuildArgs() {
    ArrayList<String> list = super.getImageBuildArgs();
    list.add("CASSANDRA_VERSION=5.0.2");
    return list;
  }
}
