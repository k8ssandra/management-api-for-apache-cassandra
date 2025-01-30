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

  @Override
  public String getUser() {
    // simulate k8s deployments with security contexts specified for the image
    return "12345:0";
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

  @Test
  public void testCqlsh() {
    assumeTrue(IntegrationTestUtils.shouldRun());
    try {
      docker.runCommand("cqlsh", "--version");
    } catch (Throwable t) {
      fail("\"cqlsh\" not installed or does not run correctly\n" + t.getLocalizedMessage());
    }
  }

  @Test
  public void testCdcAgentJarfilelinking() {
    assumeTrue(IntegrationTestUtils.shouldRun());
    try {
      // test that the CDC agent file is a symlink (does not dereference the file it links to)
      docker.runCommand("test", "-L", "/opt/cdc_agent/cdc-agent.jar");
    } catch (Throwable t) {
      fail("CDC agent jarfile at /opt/cdc_agent/cdc-agent.jar is not a symlink");
    }
    try {
      // test that the jarfile linked actually exists
      docker.runCommand("test", "-e", "/opt/cdc_agent/cdc-agent.jar");
    } catch (Throwable t) {
      fail(
          "CDC agent jarfile at /opt/cdc_agent/cdc-agent.jar links to a file that does not exist!");
    }
  }

  @Test
  public void testCassandraSocket() throws Exception {
    assumeTrue(IntegrationTestUtils.shouldRun());

    // for this test, we need Management API to start the Cassandra process
    ensureStarted();

    try {
      // see if /tmp/cassandra.sock exists and is a Socket file
      docker.runCommand("test", "-S", "/tmp/cassandra.sock");
    } catch (Throwable t) {
      fail("/tmp/cassandra.sock socket file does not exist or is not a Socket file");
    }
    if (this.version.startsWith("dse")) {
      // DSE should also have a symlink to the socket file
      try {
        docker.runCommand("test", "-L", "/tmp/dse.sock");
      } catch (Throwable t) {
        fail("/tmp/dse.sock does not exist or is not a symlink");
      }
    }
  }
}
