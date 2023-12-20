/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static com.datastax.mgmtapi.BaseDockerIntegrationTest.BASE_PATH;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.AUTH_PROVIDER_CLASS;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.AUTH_PROVIDER_PASSWORD;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.AUTH_PROVIDER_USER_NAME;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.REQUEST_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.datastax.mgmtapi.helpers.IntegrationTestUtils;
import com.datastax.mgmtapi.helpers.NettyHttpClient;
import com.datastax.mgmtapi.helpers.TestgCqlSessionBuilder;
import com.datastax.mgmtapi.resources.models.CreateOrAlterKeyspaceRequest;
import com.datastax.mgmtapi.resources.models.CreateTableRequest;
import com.datastax.mgmtapi.resources.models.ReplicationSetting;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.internal.core.auth.PlainTextAuthProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.jboss.resteasy.core.messagebody.WriterUtility;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DSESpecificIT extends BaseDockerIsolatedIntegrationTest {

  public DSESpecificIT(String version) throws IOException {
    super(version);
  }

  @Test
  public void testSai() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun() && "dse-68".equals(this.version));
    ensureStarted();
    NettyHttpClient client = new NettyHttpClient(BASE_URL);
    String localDc =
        client
            .get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
            .thenApply(this::responseAsString)
            .join();

    // this test also tests case sensitivity in CQL identifiers.
    String ks = "CreateSAITest";
    String tableName = "Table1";
    createKeyspace(client, localDc, ks);
    createTable(client, ks, tableName);

    Pair<Integer, String> response;
    URI uri;

    uri = new URIBuilder(BASE_PATH + "/ops/tables?keyspaceName=" + ks).build();
    response = client.get(uri.toURL()).thenApply(this::responseAsCodeAndBody).join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);

    List<String> actual =
        JSON_MAPPER.readValue(response.getRight(), new TypeReference<List<String>>() {});
    assertThat(actual).containsExactly(tableName);
    // try to create an index on the table
    try {
      CqlSession session =
          new TestgCqlSessionBuilder()
              .withConfigLoader(
                  DriverConfigLoader.programmaticBuilder()
                      .withString(LOAD_BALANCING_LOCAL_DATACENTER, "dc1")
                      .build())
              .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
              .build();
      ResultSet rs =
          session.execute(
              String.format(
                  "create custom index on \"%s\".\"%s\"(v) using 'StorageAttachedIndex'",
                  ks, tableName));
      assertTrue("Creating SAI failed", rs.wasApplied());
      // wait a while to make sure the server did not crash
      Thread.sleep(5000);
      rs = session.execute(String.format("select * from \"%s\".\"%s\"", ks, tableName));
      assertTrue(
          "SAI table does not contain column \"v\"", rs.getColumnDefinitions().contains("v"));
    } catch (Exception e) {
      e.printStackTrace();
      fail("Creating SAI caused a server exception: " + e.getLocalizedMessage());
    }
  }

  @Test
  public void testRebuildIndex() throws IOException, URISyntaxException {
    assumeTrue(IntegrationTestUtils.shouldRun() && "dse-68".equals(this.version));

    final String profile = "dsesearch";
    boolean ready = false;
    NettyHttpClient client = getClient();
    // Configure
    boolean configured =
        client
            .post(
                URI.create(
                        String.format("%s/lifecycle/configure?profile=%s", BASE_PATH, profile)
                            .toString())
                    .toURL(),
                FileUtils.readFileToString(
                    IntegrationTestUtils.getFile(this.getClass(), "dse-enable-search.yaml")),
                "application/yaml")
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();

    assertTrue(configured);

    // Startup
    boolean started =
        client
            .post(
                URI.create(
                        String.format("%s/lifecycle/start?profile=%s", BASE_PATH, profile)
                            .toString())
                    .toURL(),
                null)
            .thenApply(r -> r.status().code() == HttpStatus.SC_CREATED)
            .join();

    assertTrue(started);

    int tries = 0;
    while (tries++ < 30) {
      ready =
          client
              .get(URI.create(BASE_PATH + "/probes/readiness").toURL())
              .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
              .join();

      if (ready) {
        break;
      }

      Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
    }

    assertTrue(ready);
    String localDc =
        client
            .get(new URIBuilder(BASE_PATH + "/metadata/localdc").build().toURL())
            .thenApply(this::responseAsString)
            .join();

    // this test also tests case sensitivity in CQL identifiers.
    String ks = "createsaitest";
    String tableName = "tablewithsearch";
    createKeyspace(client, localDc, ks);
    createTable(client, ks, tableName);
    createSearchIndex(client, ks, tableName);

    String path =
        String.format(
            "%s/ops/node/search/rebuildIndex?keyspace=%s&table=%s", BASE_PATH, ks, tableName);
    Pair<Integer, String> response =
        client
            .post(new URIBuilder(path).build().toURL(), JSON_MAPPER.writeValueAsString(""))
            .thenApply(this::responseAsCodeAndBody)
            .join();

    // if we create the index, this should change to SC_OK
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);

    // test that we get a 404 for a non-existent search index
    tableName = "tablewithoutsearch";
    createTable(client, ks, tableName);
    path =
        String.format(
            "%s/ops/node/search/rebuildIndex?keyspace=%s&table=%s", BASE_PATH, ks, tableName);
    response =
        client
            .post(new URIBuilder(path).build().toURL(), JSON_MAPPER.writeValueAsString(""))
            .thenApply(this::responseAsCodeAndBody)
            .join();

    // if we do NOT create the index, this should respond with 404
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  private void createTable(NettyHttpClient client, String ks, String tableName)
      throws URISyntaxException, UnsupportedEncodingException, MalformedURLException,
          JsonProcessingException {
    CreateTableRequest request =
        new CreateTableRequest(
            ks,
            tableName,
            ImmutableList.of(
                // having two columns with the same name in different cases can only work if the
                // internal name is being used.
                new CreateTableRequest.Column(
                    "pk", "int", CreateTableRequest.ColumnKind.PARTITION_KEY, 0, null),
                new CreateTableRequest.Column(
                    "PK", "int", CreateTableRequest.ColumnKind.PARTITION_KEY, 1, null),
                new CreateTableRequest.Column(
                    "cc",
                    "timeuuid",
                    CreateTableRequest.ColumnKind.CLUSTERING_COLUMN,
                    0,
                    ClusteringOrder.ASC),
                new CreateTableRequest.Column(
                    "CC",
                    "timeuuid",
                    CreateTableRequest.ColumnKind.CLUSTERING_COLUMN,
                    1,
                    ClusteringOrder.DESC),
                new CreateTableRequest.Column(
                    "v", "list<text>", CreateTableRequest.ColumnKind.REGULAR, 0, null),
                new CreateTableRequest.Column(
                    "s", "boolean", CreateTableRequest.ColumnKind.STATIC, 0, null)),
            ImmutableMap.of(
                "bloom_filter_fp_chance",
                "0.01",
                "caching",
                ImmutableMap.of("keys", "ALL", "rows_per_partition", "NONE")));

    URI uri = new URIBuilder(BASE_PATH + "/ops/tables/create").build();
    Pair<Integer, String> response =
        client
            .post(uri.toURL(), JSON_MAPPER.writeValueAsString(request))
            .thenApply(this::responseAsCodeAndBody)
            .join();
    assertThat(response.getLeft()).isEqualTo(HttpStatus.SC_OK);
  }

  private void createKeyspace(NettyHttpClient client, String localDc, String keyspaceName)
      throws IOException, URISyntaxException {
    CreateOrAlterKeyspaceRequest request =
        new CreateOrAlterKeyspaceRequest(
            keyspaceName, Arrays.asList(new ReplicationSetting(localDc, 1)));
    String requestAsJSON = WriterUtility.asString(request, MediaType.APPLICATION_JSON);

    URI uri = new URIBuilder(BASE_PATH + "/ops/keyspace/create").build();
    boolean requestSuccessful =
        client
            .post(uri.toURL(), requestAsJSON)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(requestSuccessful);
  }

  private void createSearchIndex(NettyHttpClient client, String keyspaceName, String tableName)
      throws MalformedURLException, UnsupportedEncodingException {
    final String username = "dsesearch";
    final String password = "dsesearch";

    // create a CQL role we'll use to create the index
    boolean roleAdded =
        client
            .post(
                URI.create(
                        String.format(
                                "%s/ops/auth/role?username=%s&password=%s&is_superuser=true&can_login=true",
                                BASE_PATH, username, password)
                            .toString())
                    .toURL(),
                null)
            .thenApply(r -> r.status().code() == HttpStatus.SC_OK)
            .join();
    assertTrue(roleAdded);

    // login with as the role above
    CqlSession session =
        new TestgCqlSessionBuilder()
            .withConfigLoader(
                DriverConfigLoader.programmaticBuilder()
                    .withString(AUTH_PROVIDER_CLASS, PlainTextAuthProvider.class.getCanonicalName())
                    .withString(AUTH_PROVIDER_USER_NAME, username)
                    .withString(AUTH_PROVIDER_PASSWORD, password)
                    .withString(LOAD_BALANCING_LOCAL_DATACENTER, "dc1")
                    // creating the search index can take longer than the default 2 second request
                    // timeout
                    .withString(REQUEST_TIMEOUT, "20s")
                    .build())
            .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
            .build();

    session.execute(String.format("CREATE SEARCH INDEX ON %s.%s", keyspaceName, tableName));
    session.close();
  }
}
