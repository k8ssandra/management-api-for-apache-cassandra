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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DockerImageIT extends BaseDockerIntegrationTest {

  public DockerImageIT(String version) throws IOException {
    super(version);
  }

  @Test
  public void testCurlExists() {
    assumeTrue(IntegrationTestUtils.shouldRun());
    // see if curl is installed
    try {
      docker.runCommand("which", "curl");
    } catch (Throwable t) {
      // this is expected, ensure the message indicates a process error code
      fail(
          "\"curl\" was not found in the image. Please ensure it has been added to the Dockerfile");
    }
  }

  @Test
  public void testWgetExists() {
    assumeTrue(IntegrationTestUtils.shouldRun());
    // ensure wget is installed
    try {
      docker.runCommand("which", "wget");
    } catch (Throwable t) {
      fail(
          "\"wget\" was not found in the image. Please ensure it has been added to the Dockerfile");
    }
  }

  @Test
  public void testTopologyFileDoesNotExist() {
    assumeTrue(IntegrationTestUtils.shouldRun());
    final String topologyFile =
        (this.version.startsWith("dse")
                ? "/opt/dse/resources/cassandra/conf"
                : "/opt/cassandra/conf")
            + "/cassandra-topology.properties";
    try {
      docker.runCommand("test", "!", "-e", topologyFile);
    } catch (Throwable t) {
      fail(
          "\"cassandra-topology.properties\" file exists but should not. Please check entrypoint script"
              + "\n"
              + t.getLocalizedMessage());
    }
  }

  @Test
  public void testTarExist() {
    assumeTrue(IntegrationTestUtils.shouldRun());
    try {
      docker.runCommand("tar", "--version");
    } catch (Throwable t) {
      fail(
          "\"tar\" does not exist. Please check entrypoint script"
              + "\n"
              + t.getLocalizedMessage());
    }
  }
}
