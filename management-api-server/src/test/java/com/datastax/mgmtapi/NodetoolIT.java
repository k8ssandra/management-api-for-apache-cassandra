/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class NodetoolIT extends BaseDockerIntegrationTest {
  private static final Logger logger = LoggerFactory.getLogger(NodetoolIT.class);

  public NodetoolIT(String version) throws IOException {
    super(version);
  }

  /**
   * Override environment variables to specify the version of Cassandra to be used. For the test in
   * here, we want Cassandra 4.0.3 or lower, to ensure the JDK-8278972 bug doesn't come back.
   *
   * @return A list of Docker build environment variables.
   */
  @Override
  protected ArrayList<String> getImageBuildArgs() {
    ArrayList<String> list = super.getImageBuildArgs();
    list.add("CASSANDRA_VERSION=4.0.1");
    return list;
  }

  @Test
  public void testNodetool() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());
    // For now, we're only running this against Version 4.0.1
    assumeTrue(this.version.startsWith("4_0"));
    ensureStarted();
    try {
      docker.runCommand("nodetool", "status");
    } catch (Throwable t) {
      fail("Nodetool status command did not execute successfully");
    }
  }
}
