/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

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
import java.io.IOError;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.junit.AfterClass;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.Parameterized;

public abstract class BaseDockerIntegrationTest {
  protected static final String BASE_PATH = "http://localhost:8080/api/v0";
  protected static final String BASE_PATH_V1 = "http://localhost:8080/api/v1";
  protected static final String BASE_PATH_V2 = "http://localhost:8080/api/v2";
  protected static final URL BASE_URL;
  protected static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  static {
    try {
      BASE_URL = new URL(BASE_PATH);
    } catch (MalformedURLException e) {
      throw new RuntimeException();
    }
  }

  @Rule(order = Integer.MIN_VALUE)
  public TestWatcher watchman =
      new TestWatcher() {
        protected void starting(Description description) {
          System.out.println();
          System.out.println("--------------------------------------");
          System.out.printf("Starting %s...%n", description.getDisplayName());
        }

        @Override
        protected void failed(Throwable e, Description description) {
          System.out.flush();
          System.err.printf("FAILURE: %s%n", description);
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

        protected void succeeded(Description description) {
          System.out.printf("SUCCESS: %s%n", description.getDisplayName());
        }

        protected void skipped(AssumptionViolatedException e, Description description) {
          System.out.printf("SKIPPED: %s%n", description.getDisplayName());
        }

        protected void finished(Description description) {
          System.out.println("--------------------------------------");
          System.out.println();
        }
      };

  @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected final String version;
  protected static DockerHelper docker;

  @Parameterized.Parameters(name = "{index}: {0}")
  public static List<String> testVersions() {
    List<String> versions = new ArrayList<>(4);

    if (Boolean.getBoolean("run311tests")) versions.add("3_11");
    if (Boolean.getBoolean("run311testsUBI")) versions.add("3_11_ubi");
    if (Boolean.getBoolean("run40tests")) versions.add("4_0");
    if (Boolean.getBoolean("run40testsUBI")) versions.add("4_0_ubi");
    if (Boolean.getBoolean("run41tests")) versions.add("4_1");
    if (Boolean.getBoolean("run41testsUBI")) versions.add("4_1_ubi");
    if (Boolean.getBoolean("run50testsUBI")) versions.add("5_0_ubi");
    if (Boolean.getBoolean("runTrunktests")) versions.add("trunk");
    if (Boolean.getBoolean("runDSEtests")) versions.add("dse-68");
    if (Boolean.getBoolean("runDSEtestsUBI")) versions.add("dse-68_ubi");

    return versions;
  }

  public BaseDockerIntegrationTest(String version) throws IOException {
    this.version = version;

    // If run without forking we need to start a new version
    if (docker != null) {
      temporaryFolder.delete();
      temporaryFolder.create();
      docker.startManagementAPI(version, getEnvironmentVars(), getUser());
    }
  }

  @BeforeClass
  public static void setup() throws InterruptedException {
    try {
      temporaryFolder.create();
      docker = new DockerHelper(getTempDir());
      docker.removeExistingCntainers();
    } catch (IOException e) {
      throw new IOError(e);
    }
  }

  @AfterClass
  public static void teardown() {
    try {
      docker.stopManagementAPI();
    } finally {
      // temporaryFolder.delete();
    }
  }

  @Before
  public void before() {
    if (!docker.started()) {
      docker.startManagementAPI(version, getEnvironmentVars(), getUser());
    }
  }

  protected ArrayList<String> getEnvironmentVars() {
    return Lists.newArrayList(
        "MGMT_API_NO_KEEP_ALIVE=true",
        "MGMT_API_EXPLICIT_START=true",
        "DSE_MGMT_NO_KEEP_ALIVE=true",
        "DSE_MGMT_EXPLICIT_START=true");
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
    File tempDir = temporaryFolder.getRoot();
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
    if (this.version.startsWith("3")) {
      return 256;
    }
    if (this.version.startsWith("dse-68")) {
      return 1;
    }
    if (this.version.startsWith("4")) {
      return 16;
    }
    if (this.version.startsWith("5")) {
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
