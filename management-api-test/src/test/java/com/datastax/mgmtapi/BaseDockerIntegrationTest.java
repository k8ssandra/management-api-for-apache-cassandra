/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.datastax.mgmtapi.helpers.DockerHelper;
import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.resources.models.CreateOrAlterKeyspaceRequest;
import com.datastax.mgmtapi.resources.models.ReplicationSetting;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import io.netty.handler.codec.http.FullHttpResponse;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;

public abstract class BaseDockerIntegrationTest {
  protected static final String BASE_PATH = "http://localhost:8080/api/v0";
  protected static final String BASE_PATH_V1 = "http://localhost:8080/api/v1";
  protected static final String BASE_PATH_V2 = "http://localhost:8080/api/v2";
  protected static final URL BASE_URL;
  protected static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  static Path temporaryFolder;

  static {
    try {
      BASE_URL = new URL(BASE_PATH);
    } catch (MalformedURLException e) {
      throw new RuntimeException();
    }
  }

  @RegisterExtension
  static final TestWatcher watchman =
      new TestWatcher() {
        @Override
        public void testFailed(ExtensionContext context, Throwable e) {
          System.out.flush();
          System.err.printf("FAILURE: %s%n", context.getDisplayName());
          e.printStackTrace();
          System.err.flush();

          if (null != docker) {
            int numberOfLines = 1000;
            System.out.printf("=====> Showing last %d entries of system.log%n", numberOfLines);
            docker.tailSystemLog(numberOfLines);
            System.out.printf("=====> End of last %d entries of system.log%n", numberOfLines);
            System.out.flush();
          }
        }

        @Override
        public void testSuccessful(ExtensionContext context) {
          System.out.printf("SUCCESS: %s%n", context.getDisplayName());
        }

        @Override
        public void testAborted(ExtensionContext context, Throwable cause) {
          System.out.printf("SKIPPED: %s%n", context.getDisplayName());
        }
      };

  protected final String version;
  protected static DockerHelper docker;

  public static List<String> testVersions() {
    List<String> versions = new ArrayList<>(4);

    if (Boolean.getBoolean("run4.0tests")) versions.add("4_0");
    if (Boolean.getBoolean("run4.0testsUBI")) versions.add("4_0_ubi");
    if (Boolean.getBoolean("run4.1tests")) versions.add("4_1");
    if (Boolean.getBoolean("run4.1testsUBI")) versions.add("4_1_ubi");
    if (Boolean.getBoolean("run5.0testsUBI")) versions.add("5_0_ubi");
    if (Boolean.getBoolean("run6.0testsUBI")) versions.add("6_0_ubi");
    if (Boolean.getBoolean("runtrunktestsUBI")) versions.add("trunk_ubi");
    if (Boolean.getBoolean("runDSE6.8tests")) versions.add("dse-68");
    if (Boolean.getBoolean("runDSE6.8testsUBI")) versions.add("dse-68_ubi");
    if (Boolean.getBoolean("runDSE6.9tests")) versions.add("dse-69");
    if (Boolean.getBoolean("runDSE6.9testsUBI")) versions.add("dse-69_ubi");

    return versions;
  }

  public BaseDockerIntegrationTest(String version) throws IOException {
    this.version = version;

    // If run without forking we need to start a new version
    if (docker != null) {
      docker.startManagementAPI(version, getEnvironmentVars(), getUser(), getImageBuildArgs());
    }
  }

  @BeforeParameterizedClassInvocation(injectArguments = false)
  public static void setup(@TempDir Path tempDir) {
    temporaryFolder = tempDir;
    docker = new DockerHelper(getTempDir());
    docker.removeExistingCntainers();
  }

  @AfterParameterizedClassInvocation(injectArguments = false)
  public static void teardown() {
    if (docker != null) {
      docker.stopManagementAPI();
      docker = null;
    }
  }

  @BeforeEach
  public void before() throws IOException {
    if (!docker.started()) {
      docker.startManagementAPI(version, getEnvironmentVars(), getUser(), getImageBuildArgs());
    }
  }

  protected ArrayList<String> getEnvironmentVars() {
    return Lists.newArrayList(
        "MGMT_API_NO_KEEP_ALIVE=true",
        "MGMT_API_EXPLICIT_START=true",
        "DSE_MGMT_NO_KEEP_ALIVE=true",
        "DSE_MGMT_EXPLICIT_START=true");
  }

  /**
   * Returns a list of image build variables to provide when building the Docker image to test. An
   * example of this in in NodetoolIT where we specify the Cassandra version to test against a
   * specific version for a known bug. Most tests should not need to override this.
   *
   * @return A list of Docker build environment variables.
   */
  protected ArrayList<String> getImageBuildArgs() {
    return Lists.newArrayList();
  }

  protected String getUser() {
    // The default should be either "cassandra:root" or "dse:root"
    // Subclasses can override this to test alternate user behavior
    if (this.version.startsWith("dse")) {
      return "dse:root";
    }
    return "cassandra:root";
  }

  protected static File getTempDir() {
    String os = System.getProperty("os.name");
    File tempDir = temporaryFolder.toFile();
    if (os.equalsIgnoreCase("mac os x")) {
      tempDir = new File("/private", tempDir.getPath());
    }

    tempDir.setWritable(true, false);
    tempDir.setReadable(true, false);
    tempDir.setExecutable(true, false);

    return tempDir;
  }

  protected NettyHttpClient getClient() throws SSLException {
    return new NettyHttpClient(BASE_URL);
  }

  protected void createKeyspace(NettyHttpClient client, String localDc, String keyspaceName, int rf)
      throws IOException, URISyntaxException {
    CreateOrAlterKeyspaceRequest request =
        new CreateOrAlterKeyspaceRequest(
            keyspaceName, Arrays.asList(new ReplicationSetting(localDc, rf)));
    String requestAsJSON = JSON_MAPPER.writeValueAsString(request);

    URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace/create").build();
    boolean requestSuccessful =
        client
            .post(uri.toURL(), requestAsJSON)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(requestSuccessful);
  }

  protected String responseAsString(FullHttpResponse r) {
    if (r.status().code() == HttpStatus.SC_OK) {
      byte[] result = new byte[r.content().readableBytes()];
      r.content().readBytes(result);

      return new String(result);
    }

    return null;
  }

  protected Pair<Integer, String> responseAsCodeAndBody(FullHttpResponse r) {
    FullHttpResponse copy = r.copy();
    if (copy.content().readableBytes() > 0) {
      return Pair.of(copy.status().code(), copy.content().toString(UTF_8));
    }

    return Pair.of(copy.status().code(), null);
  }

  protected int getNumTokenRanges() {
    if (this.version.startsWith("dse")) {
      return 1;
    }
    if (this.version.startsWith("4")) {
      return 16;
    }
    if (this.version.startsWith("5")) {
      return 16;
    }
    if (this.version.startsWith("6")) {
      return 16;
    }
    if (this.version.startsWith("trunk")) {
      return 16;
    }
    // unsupported Cassandra/DSE version
    throw new UnsupportedOperationException("Cassandra version " + this.version + " not supported");
  }

  protected void ensureStarted() throws IOException {
    assumeTrue(IntegrationTestUtils.shouldRun());

    NettyHttpClient client = new NettyHttpClient(BASE_URL);

    // Verify liveness
    boolean live =
        client
            .get(URI.create(BASE_PATH + "/probes/liveness").toURL())
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();

    assertTrue(live);

    boolean ready = false;

    // Startup
    boolean started =
        client
            .post(URI.create(BASE_PATH + "/lifecycle/start").toURL(), null)
            .thenApply(
                r ->
                    r.status().code() == HttpStatus.SC_CREATED
                        || r.status().code() == HttpStatus.SC_ACCEPTED)
            .join();

    assertTrue(started);

    int tries = 0;
    while (tries++ < 10) {
      ready =
          client
              .get(URI.create(BASE_PATH + "/probes/readiness").toURL())
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      if (ready) break;

      Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
    }

    assertTrue(ready);
  }
}
