/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.rpc.Rpc;
import com.datastax.mgmtapi.rpc.RpcParam;
import com.datastax.mgmtapi.rpc.RpcRegistry;
import com.datastax.mgmtapi.util.Job;
import com.datastax.mgmtapi.util.JobExecutor;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTable;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTableWithOptions;
import com.datastax.oss.driver.api.querybuilder.schema.OngoingPartitionKey;
import com.datastax.oss.driver.internal.core.metadata.schema.parsing.DataTypeCqlNameParser;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;
import io.k8ssandra.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import io.k8ssandra.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.compaction.OperationType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.repair.RepairParallelism;
import org.apache.cassandra.repair.messages.RepairOption;
import org.apache.cassandra.service.StorageProxy;
import org.apache.cassandra.utils.Pair;
import org.apache.cassandra.utils.progress.ProgressEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Replace JMX calls with CQL 'CALL' methods via the Rpc framework */
public class NodeOpsProvider {
  private static final Logger logger = LoggerFactory.getLogger(NodeOpsProvider.class);
  public static final Supplier<NodeOpsProvider> instance =
      Suppliers.memoize(() -> new NodeOpsProvider());
  public static JobExecutor service = new JobExecutor();

  public static final String RPC_CLASS_NAME = "NodeOps";

  private static final DataTypeCqlNameParser DATA_TYPE_PARSER = new DataTypeCqlNameParser();

  @VisibleForTesting
  protected NodeOpsProvider() {}

  public synchronized void register() {
    RpcRegistry.register(RPC_CLASS_NAME, this);
  }

  public synchronized void unregister() {
    RpcRegistry.unregister(RPC_CLASS_NAME);
  }

  @Rpc(name = "getJobStatus")
  public Map<String, String> getJobStatus(@RpcParam(name = "job_id") String jobId) {
    Map<String, String> resultMap = new HashMap<>();
    Job jobWithId = service.getJobWithId(jobId);
    if (jobWithId == null) {
      return resultMap;
    }
    resultMap.put("id", jobWithId.getJobId());
    resultMap.put("type", jobWithId.getJobType());
    resultMap.put("status", jobWithId.getStatus().name());
    resultMap.put("submit_time", String.valueOf(jobWithId.getSubmitTime()));
    resultMap.put("end_time", String.valueOf(jobWithId.getFinishedTime()));
    if (jobWithId.getStatus() == Job.JobStatus.ERROR) {
      logger.error("Task " + jobId + " execution failed", jobWithId.getError());
      resultMap.put("error", jobWithId.getError().getLocalizedMessage());
    }

    List<Map<String, String>> statusChanges = new ArrayList<>();
    for (Job.StatusChange statusChange : jobWithId.getStatusChanges()) {
      Map<String, String> change = Maps.newHashMap();
      change.put("status", statusChange.getStatus().name());
      change.put("change_time", Long.valueOf(statusChange.getChangeTime()).toString());
      change.put("message", statusChange.getMessage());
      statusChanges.add(change);
    }

    ObjectMapper objectMapper = new ObjectMapper();
    try {
      String s = objectMapper.writeValueAsString(statusChanges);
      resultMap.put("status_changes", s);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    return resultMap;
  }

  @Rpc(name = "setFullQuerylog")
  public void setFullQuerylog(@RpcParam(name = "enabled") boolean fullQueryLoggingEnabled)
      throws UnsupportedOperationException {
    logger.debug("Attempting to set full query logging to " + fullQueryLoggingEnabled);
    // Warning: bad design here. Default implementation will throw UnsupportedOperationException at
    // runtime. Comparing strings to get versions is also ugly.
    String cassVersion = ShimLoader.instance.get().getStorageService().getReleaseVersion();
    if (Integer.parseInt(cassVersion.split("\\.")[0]) < 4) {
      logger.error(
          "Full query logging is not available in Cassandra < 4x. This call is going to fail.");
    }
    if (fullQueryLoggingEnabled) {
      ShimLoader.instance.get().enableFullQuerylog();
    } else {
      ShimLoader.instance.get().disableFullQuerylog();
    }
  }

  @Rpc(name = "isFullQueryLogEnabled")
  public boolean isFullQueryLogEnabled() throws UnsupportedOperationException {
    String cassVersion = ShimLoader.instance.get().getStorageService().getReleaseVersion();
    logger.debug(
        "Attempting to retrieve full query logging status for cassandra version"
            + cassVersion
            + ".");
    if (Integer.parseInt(cassVersion.split("\\.")[0]) < 4) {
      logger.error(
          "Full query logging is not available in Cassandra < 4x. This call is going to fail.");
    }
    logger.debug("Calling ShimLoader.instance.get().isFullQueryLogEnabled()");
    return ShimLoader.instance.get().isFullQueryLogEnabled();
  }

  @Rpc(name = "reloadSeeds")
  public List<String> reloadSeeds() {
    logger.debug("Reloading Seeds");
    Set<InetAddress> seeds = ShimLoader.instance.get().reloadSeeds();
    if (seeds == null) throw new RuntimeException("Error reloading seeds");

    return seeds.stream().map(InetAddress::toString).collect(Collectors.toList());
  }

  @Rpc(name = "getReleaseVersion")
  public String getReleaseVersion() {
    logger.debug("Getting Release Version");
    return ShimLoader.instance.get().getStorageService().getReleaseVersion();
  }

  @Rpc(name = "decommission")
  public String decommission(
      @RpcParam(name = "force") boolean force, @RpcParam(name = "async") boolean async)
      throws InterruptedException {
    logger.debug("Decommissioning");
    // Send to background execution and return a job number
    Pair<String, CompletableFuture<Void>> jobPair =
        service.submit(
            "decommission",
            () -> {
              try {
                ShimLoader.instance.get().decommission(force);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            });

    if (!async) {
      jobPair.right.join();
    }

    return jobPair.left;
  }

  @Rpc(name = "rebuild")
  public String rebuild(@RpcParam(name = "srcDatacenter") String srcDatacenter) {
    logger.debug("Starting rebuild");

    Pair<String, CompletableFuture<Void>> jobPair =
        service.submit("rebuild", () -> ShimLoader.instance.get().rebuild(srcDatacenter));

    return jobPair.left;
  }

  @Rpc(name = "setCompactionThroughput")
  public void setCompactionThroughput(@RpcParam(name = "value") int value) {
    logger.debug("Setting compaction throughput to {}", value);
    ShimLoader.instance.get().getStorageService().setCompactionThroughputMbPerSec(value);
  }

  @Rpc(name = "assassinate")
  public void assassinate(@RpcParam(name = "address") String address) throws UnknownHostException {
    logger.debug("Assassinating {}", address);
    ShimLoader.instance.get().getGossiper().assassinateEndpoint(address);
  }

  @Rpc(name = "setLoggingLevel")
  public void setLoggingLevel(
      @RpcParam(name = "classQualifier") String classQualifier,
      @RpcParam(name = "level") String level)
      throws Exception {
    logger.debug("Setting logging level of {} to level {}", classQualifier, level);
    ShimLoader.instance.get().getStorageService().setLoggingLevel(classQualifier, level);
  }

  @Rpc(name = "drain")
  public void drain() throws InterruptedException, ExecutionException, IOException {
    logger.debug("Draining");
    ShimLoader.instance.get().getStorageService().drain();
  }

  @Rpc(name = "truncateAllHints")
  public void truncateHints() {
    logger.debug("Truncating all hints");
    ShimLoader.instance.get().getHintsService().deleteAllHints();
  }

  @Rpc(name = "truncateHintsForHost")
  public void truncateHints(@RpcParam(name = "host") String host) {
    logger.debug("Truncating hints for host {}", host);
    ShimLoader.instance.get().getHintsService().deleteAllHintsForEndpoint(host);
  }

  @Rpc(name = "resetLocalSchema")
  public void resetLocalSchema() throws IOException {
    logger.debug("Resetting local schema");
    ShimLoader.instance.get().getStorageService().resetLocalSchema();
  }

  @Rpc(name = "reloadLocalSchema")
  public void reloadLocalSchema() {
    logger.debug("Reloading local schema");
    ShimLoader.instance.get().getStorageService().reloadLocalSchema();
  }

  private String submitJob(String operationName, Runnable operation, boolean async) {
    Pair<String, CompletableFuture<Void>> jobPair = service.submit(operationName, operation);

    if (!async) {
      jobPair.right.join();
    }

    return jobPair.left;
  }

  @Rpc(name = "upgradeSSTables")
  public String upgradeSSTables(
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "excludeCurrentVersion") boolean excludeCurrentVersion,
      @RpcParam(name = "jobs") int jobs,
      @RpcParam(name = "tableNames") List<String> tableNames,
      @RpcParam(name = "async") boolean async)
      throws IOException, ExecutionException, InterruptedException {
    logger.debug("Upgrading SSTables");

    final List<String> keyspaces = new ArrayList<>();
    if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL")) {
      keyspaces.addAll(ShimLoader.instance.get().getStorageService().getKeyspaces());
    } else {
      keyspaces.add(keyspaceName);
    }

    Runnable upgradeOperation =
        () -> {
          for (String keyspace : keyspaces) {
            try {
              ShimLoader.instance
                  .get()
                  .getStorageService()
                  .upgradeSSTables(
                      keyspace, excludeCurrentVersion, jobs, tableNames.toArray(new String[] {}));
            } catch (IOException | ExecutionException | InterruptedException e) {
              logger.error("Failed to execute upgradeSSTables in " + keyspace, e);
              throw new RuntimeException(e);
            }
          }
        };

    // Send to background execution and return job number
    return submitJob(OperationType.UPGRADE_SSTABLES.name(), upgradeOperation, async);
  }

  @Rpc(name = "forceKeyspaceCleanup")
  public String forceKeyspaceCleanup(
      @RpcParam(name = "jobs") int jobs,
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "tables") List<String> tables,
      @RpcParam(name = "async") boolean async)
      throws InterruptedException, ExecutionException, IOException {
    logger.debug("Forcing cleanup on keyspace {}", keyspaceName);
    final List<String> keyspaces = new ArrayList<>();
    if (keyspaceName != null && keyspaceName.equalsIgnoreCase("NON_LOCAL_STRATEGY")) {
      keyspaces.addAll(
          ShimLoader.instance.get().getStorageService().getNonLocalStrategyKeyspaces());
    } else {
      keyspaces.add(keyspaceName);
    }

    Runnable cleanupOperation =
        () -> {
          for (String keyspace : keyspaces) {
            try {
              ShimLoader.instance
                  .get()
                  .getStorageService()
                  .forceKeyspaceCleanup(jobs, keyspace, tables.toArray(new String[] {}));
            } catch (IOException | ExecutionException | InterruptedException e) {
              logger.error("Failed to execute forceKeyspaceCleanup in " + keyspace, e);
              throw new RuntimeException(e);
            }
          }
        };

    // Send to background execution and return job number
    return submitJob(OperationType.CLEANUP.name(), cleanupOperation, async);
  }

  @Rpc(name = "forceKeyspaceCompactionForTokenRange")
  public String forceKeyspaceCompactionForTokenRange(
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "startToken") String startToken,
      @RpcParam(name = "endToken") String endToken,
      @RpcParam(name = "tableNames") List<String> tableNames,
      @RpcParam(name = "async") boolean async)
      throws InterruptedException, ExecutionException, IOException {
    logger.debug("Forcing keyspace compaction for token range on keyspace {}", keyspaceName);

    final List<String> keyspaces = new ArrayList<>();
    if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL")) {
      keyspaces.addAll(ShimLoader.instance.get().getStorageService().getKeyspaces());
    } else {
      keyspaces.add(keyspaceName);
    }

    Runnable compactionOperation =
        () -> {
          for (String keyspace : keyspaces) {
            try {
              ShimLoader.instance
                  .get()
                  .getStorageService()
                  .forceKeyspaceCompactionForTokenRange(
                      keyspace, startToken, endToken, tableNames.toArray(new String[] {}));
            } catch (IOException | ExecutionException | InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        };

    return submitJob(OperationType.COMPACTION.name(), compactionOperation, async);
  }

  @Rpc(name = "forceKeyspaceCompaction")
  public String forceKeyspaceCompaction(
      @RpcParam(name = "splitOutput") boolean splitOutput,
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "tableNames") List<String> tableNames,
      @RpcParam(name = "async") boolean async)
      throws InterruptedException, ExecutionException, IOException {
    logger.debug("Forcing keyspace compaction on keyspace {}", keyspaceName);

    final List<String> keyspaces = new ArrayList<>();
    if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL")) {
      keyspaces.addAll(ShimLoader.instance.get().getStorageService().getKeyspaces());
    } else {
      keyspaces.add(keyspaceName);
    }

    Runnable compactionOperation =
        () -> {
          for (String keyspace : keyspaces) {
            try {
              ShimLoader.instance
                  .get()
                  .getStorageService()
                  .forceKeyspaceCompaction(
                      splitOutput, keyspace, tableNames.toArray(new String[] {}));
            } catch (IOException | ExecutionException | InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        };

    return submitJob(OperationType.COMPACTION.name(), compactionOperation, async);
  }

  @Rpc(name = "getCompactions")
  public List<Map<String, String>> getCompactions() {
    logger.debug("Getting active compactions");
    return ShimLoader.instance.get().getCompactionManager().getCompactions();
  }

  @Rpc(name = "garbageCollect")
  public String garbageCollect(
      @RpcParam(name = "tombstoneOption") String tombstoneOption,
      @RpcParam(name = "jobs") int jobs,
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "tableNames") List<String> tableNames,
      @RpcParam(name = "async") boolean async)
      throws InterruptedException, ExecutionException, IOException {
    logger.debug("Garbage collecting on keyspace {}", keyspaceName);
    List<String> keyspaces = Collections.singletonList(keyspaceName);
    if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL")) {
      keyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
    }

    final List<String> keyspaceList = keyspaces;

    Runnable garbageCollectOperation =
        () -> {
          for (String keyspace : keyspaceList) {
            try {
              ShimLoader.instance
                  .get()
                  .getStorageService()
                  .garbageCollect(
                      tombstoneOption, jobs, keyspace, tableNames.toArray(new String[] {}));
            } catch (IOException | ExecutionException | InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        };

    return submitJob(OperationType.GARBAGE_COLLECT.name(), garbageCollectOperation, async);
  }

  @Rpc(name = "loadNewSSTables")
  public void loadNewSSTables(
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "table") String table) {
    logger.debug("Forcing keyspace refresh on keyspace {} and table {}", keyspaceName, table);
    ShimLoader.instance.get().getStorageService().loadNewSSTables(keyspaceName, table);
  }

  @Rpc(name = "forceKeyspaceFlush")
  public String forceKeyspaceFlush(
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "tableNames") List<String> tableNames,
      @RpcParam(name = "async") boolean async)
      throws IOException {
    logger.debug("Forcing keyspace flush on keyspace {}", keyspaceName);
    List<String> keyspaces = Collections.singletonList(keyspaceName);
    if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL")) {
      keyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
    }

    final List<String> keyspaceList = keyspaces;

    Runnable flushOperation =
        () -> {
          for (String keyspace : keyspaceList) {
            try {
              ShimLoader.instance
                  .get()
                  .getStorageService()
                  .forceKeyspaceFlush(keyspace, tableNames.toArray(new String[] {}));
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        };

    return submitJob(OperationType.FLUSH.name(), flushOperation, async);
  }

  @Rpc(name = "scrub")
  public String scrub(
      @RpcParam(name = "disableSnapshot") boolean disableSnapshot,
      @RpcParam(name = "skipCorrupted") boolean skipCorrupted,
      @RpcParam(name = "checkData") boolean checkData,
      @RpcParam(name = "reinsertOverflowedTTL") boolean reinsertOverflowedTTL,
      @RpcParam(name = "jobs") int jobs,
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "tables") List<String> tables,
      @RpcParam(name = "async") boolean async)
      throws InterruptedException, ExecutionException, IOException {
    logger.debug("Scrubbing tables on keyspace {}", keyspaceName);
    List<String> keyspaces = Collections.singletonList(keyspaceName);
    if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL")) {
      keyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
    }

    final List<String> keyspaceList = keyspaces;

    Runnable scrubOperation =
        () -> {
          for (String keyspace : keyspaceList) {
            try {
              ShimLoader.instance
                  .get()
                  .getStorageService()
                  .scrub(
                      disableSnapshot,
                      skipCorrupted,
                      checkData,
                      reinsertOverflowedTTL,
                      jobs,
                      keyspace,
                      tables.toArray(new String[] {}));
            } catch (IOException | ExecutionException | InterruptedException e) {
              logger.error("Failed to execute scrub: ", e);
              throw new RuntimeException(e);
            }
          }
        };
    return submitJob(OperationType.SCRUB.name(), scrubOperation, async);
  }

  @Rpc(name = "forceUserDefinedCompaction")
  public String forceUserDefinedCompaction(
      @RpcParam(name = "datafiles") String datafiles, @RpcParam(name = "async") boolean async) {
    logger.debug("Forcing user defined compaction");
    Runnable compactOperation =
        () -> {
          ShimLoader.instance.get().getCompactionManager().forceUserDefinedCompaction(datafiles);
        };
    return submitJob(OperationType.COMPACTION.name(), compactOperation, async);
  }

  @Rpc(name = "createRole")
  public void createRole(
      @RpcParam(name = "username") String username,
      @RpcParam(name = "superuser") Boolean superUser,
      @RpcParam(name = "login") Boolean login,
      @RpcParam(name = "password") String password) {
    logger.debug("Creating role {}", username);
    RoleResource rr = RoleResource.role(username);
    RoleOptions ro = new RoleOptions();
    ro.setOption(IRoleManager.Option.SUPERUSER, superUser);
    ro.setOption(IRoleManager.Option.LOGIN, login);
    ro.setOption(IRoleManager.Option.PASSWORD, password);

    ShimLoader.instance.get().getRoleManager().createRole(AuthenticatedUser.SYSTEM_USER, rr, ro);
  }

  @Rpc(name = "listRoles")
  public List<Map<String, String>> listRoles() {
    logger.debug("Listing roles");
    return ShimLoader.instance.get().listRoles();
  }

  @Rpc(name = "dropRole")
  public void dropRole(@RpcParam(name = "username") String username) {
    logger.debug("Dropping role {}", username);
    RoleResource rr = RoleResource.role(username);

    ShimLoader.instance.get().getRoleManager().dropRole(AuthenticatedUser.SYSTEM_USER, rr);
  }

  @Rpc(name = "checkConsistencyLevel")
  public Map<List<Long>, List<String>> checkConsistencyLevel(
      @RpcParam(name = "consistency_level") String consistencyLevelName,
      @RpcParam(name = "rf_per_dc") Integer rfPerDc) {
    logger.debug("Checking cl={} assuming {} replicas per node", consistencyLevelName, rfPerDc);
    Preconditions.checkArgument(consistencyLevelName != null, "consistency_level must be defined");
    Preconditions.checkArgument(rfPerDc != null, "rf_per_dc must be defined");
    Preconditions.checkArgument(rfPerDc > 0, "rf_per_dc must be > 0");

    return ShimLoader.instance.get().checkConsistencyLevel(consistencyLevelName, rfPerDc);
  }

  @Rpc(name = "getEndpointStates")
  public List<Map<String, String>> getEndpointStates() {
    return ShimLoader.instance.get().getEndpointStates();
  }

  @Rpc(name = "getStreamInfo")
  public List<Map<String, List<Map<String, String>>>> getStreamInfo() {
    return ShimLoader.instance.get().getStreamInfo();
  }

  @Rpc(name = "getSchemaVersions")
  public Map<String, List<String>> getSchemaVersions() {
    return StorageProxy.instance.getSchemaVersions();
  }

  @Rpc(name = "getKeyspaces")
  public List<String> getKeyspaces() {
    return ShimLoader.instance.get().getKeyspaces();
  }

  @Rpc(name = "getReplication")
  public Map<String, String> getReplication(@RpcParam(name = "keyspaceName") String keyspaceName) {
    String query =
        QueryBuilder.selectFrom("system_schema", "keyspaces")
            .column("replication")
            .where(Relation.column("keyspace_name").isEqualTo(QueryBuilder.literal(keyspaceName)))
            .asCql();
    UntypedResultSet rows = ShimLoader.instance.get().processQuery(query, ConsistencyLevel.ONE);
    if (rows.isEmpty()) {
      return null;
    }
    return rows.one().getMap("replication", UTF8Type.instance, UTF8Type.instance);
  }

  @Rpc(name = "getTables", multiRow = true)
  public List<Table> getTables(@RpcParam(name = "keyspaceName") String keyspaceName) {
    String query =
        QueryBuilder.selectFrom("system_schema", "tables")
            .column("table_name")
            .column("compaction")
            .where(Relation.column("keyspace_name").isEqualTo(QueryBuilder.literal(keyspaceName)))
            .asCql();
    UntypedResultSet rows = ShimLoader.instance.get().processQuery(query, ConsistencyLevel.ONE);
    List<Table> tables = new ArrayList<>();
    for (UntypedResultSet.Row row : rows) {
      tables.add(new Table(row.getString("table_name"), row.getFrozenTextMap("compaction")));
    }
    return tables;
  }

  @Rpc(name = "createKeyspace")
  public void createKeyspace(
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "replicationSettings") Map<String, Integer> replicationSettings)
      throws IOException {
    logger.debug(
        "Creating keyspace {} with replication settings {}", keyspaceName, replicationSettings);

    ShimLoader.instance
        .get()
        .processQuery(
            SchemaBuilder.createKeyspace(CqlIdentifier.fromInternal(keyspaceName))
                .ifNotExists()
                .withNetworkTopologyStrategy(replicationSettings)
                .asCql(),
            ConsistencyLevel.ONE);
  }

  @Rpc(name = "createTable")
  public void createTable(
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "tableName") String tableName,
      @RpcParam(name = "columnsAndTypes") Map<String, String> columnsAndTypes,
      @RpcParam(name = "partitionKeyColumnNames") List<String> partitionKeyColumnNames,
      @RpcParam(name = "clusteringColumnNames") List<String> clusteringColumnNames,
      @RpcParam(name = "clusteringOrders") List<String> clusteringOrders,
      @RpcParam(name = "staticColumnNames") List<String> staticColumnNames,
      @RpcParam(name = "simpleOptions") Map<String, String> simpleOptions,
      @RpcParam(name = "complexOptions") Map<String, Map<String, String>> complexOptions) {
    logger.debug("Creating table {}", tableName);
    CqlIdentifier keyspaceId = CqlIdentifier.fromInternal(keyspaceName);
    CqlIdentifier tableId = CqlIdentifier.fromInternal(tableName);
    OngoingPartitionKey stmtStart = SchemaBuilder.createTable(keyspaceId, tableId).ifNotExists();
    for (String name : partitionKeyColumnNames) {
      DataType dt = DATA_TYPE_PARSER.parse(keyspaceId, columnsAndTypes.get(name), null, null);
      stmtStart = stmtStart.withPartitionKey(CqlIdentifier.fromInternal(name), dt);
    }
    CreateTable stmt = (CreateTable) stmtStart;
    for (String name : clusteringColumnNames) {
      DataType dt = DATA_TYPE_PARSER.parse(keyspaceId, columnsAndTypes.get(name), null, null);
      stmt = stmt.withClusteringColumn(CqlIdentifier.fromInternal(name), dt);
    }
    for (String name : staticColumnNames) {
      DataType dt = DATA_TYPE_PARSER.parse(keyspaceId, columnsAndTypes.get(name), null, null);
      stmt = stmt.withStaticColumn(CqlIdentifier.fromInternal(name), dt);
    }
    for (String name : columnsAndTypes.keySet()) {
      if (!partitionKeyColumnNames.contains(name)
          && !clusteringColumnNames.contains(name)
          && !staticColumnNames.contains(name)) {
        DataType dt = DATA_TYPE_PARSER.parse(keyspaceId, columnsAndTypes.get(name), null, null);
        stmt = stmt.withColumn(CqlIdentifier.fromInternal(name), dt);
      }
    }
    CreateTableWithOptions stmtFinal = stmt;
    for (int i = 0; i < clusteringColumnNames.size(); i++) {
      String name = clusteringColumnNames.get(i);
      ClusteringOrder order =
          ClusteringOrder.valueOf(clusteringOrders.get(i).toUpperCase(Locale.ROOT));
      stmtFinal = stmtFinal.withClusteringOrder(CqlIdentifier.fromInternal(name), order);
    }
    for (Map.Entry<String, String> entry : simpleOptions.entrySet()) {
      stmtFinal = stmtFinal.withOption(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, Map<String, String>> entry : complexOptions.entrySet()) {
      stmtFinal = stmtFinal.withOption(entry.getKey(), entry.getValue());
    }
    String query = stmtFinal.asCql();
    logger.debug("Generated query: {}", query);
    ShimLoader.instance.get().processQuery(query, ConsistencyLevel.ONE);
    logger.debug("Table successfully created: {}", tableId);
  }

  @Rpc(name = "getLocalDataCenter")
  public String getLocalDataCenter() {
    return ShimLoader.instance.get().getLocalDataCenter();
  }

  @Rpc(name = "alterKeyspace")
  public void alterKeyspace(
      @RpcParam(name = "keyspaceName") String keyspaceName,
      @RpcParam(name = "replicationSettings") Map<String, Integer> replicationSettings)
      throws IOException {
    logger.debug(
        "Creating keyspace {} with replication settings {}", keyspaceName, replicationSettings);

    ShimLoader.instance
        .get()
        .processQuery(
            SchemaBuilder.alterKeyspace(CqlIdentifier.fromInternal(keyspaceName))
                .withNetworkTopologyStrategy(replicationSettings)
                .asCql(),
            ConsistencyLevel.ONE);
  }

  @Rpc(name = "getSnapshotDetails")
  public List<Map<String, String>> getSnapshotDetails(
      @RpcParam(name = "snapshotNames") List<String> snapshotNames,
      @RpcParam(name = "keyspaces") List<String> keyspaces) {
    logger.debug(
        "Fetching snapshots with snapshot names {} and keyspaces {}", snapshotNames, keyspaces);
    List<Map<String, String>> detailsList = new ArrayList<>();
    // get the map of snapshots
    Map<String, TabularData> snapshots =
        ShimLoader.instance.get().getStorageService().getSnapshotDetails();
    for (Map.Entry<String, TabularData> entry : snapshots.entrySet()) {
      // create the map of data per snapshot name
      String snapshotTag = entry.getKey();
      if (snapshotNames == null || snapshotNames.isEmpty() || snapshotNames.contains(snapshotTag)) {
        TabularData tabularData = entry.getValue();
        for (CompositeDataSupport compositeData :
            (Collection<CompositeDataSupport>) (tabularData.values())) {
          String keyspaceName = compositeData.get("Keyspace name").toString();
          if (keyspaces == null || keyspaces.isEmpty() || keyspaces.contains(keyspaceName)) {
            Map<String, String> detailsMap = new HashMap<>();
            for (String itemName : compositeData.getCompositeType().keySet()) {
              Object item = compositeData.get(itemName);
              String value = item == null ? "null" : item.toString();
              detailsMap.put(itemName, value);
            }
            detailsList.add(detailsMap);
          }
        }
      }
    }
    return detailsList;
  }

  @Rpc(name = "takeSnapshot")
  public void takeSnapshot(
      @RpcParam(name = "snapshotName") String snapshotName,
      @RpcParam(name = "keyspaces") List<String> keyspaces,
      @RpcParam(name = "tableName") String tableName,
      @RpcParam(name = "skipFlush") Boolean skipFlush,
      @RpcParam(name = "keyspaceTables") List<String> keyspaceTables)
      throws IOException {
    // skipFlush options map
    Map<String, String> optionsMap = new HashMap<>();
    optionsMap.put("skipFlush", skipFlush.toString());

    // build entities array
    String[] entities = null;
    if (tableName != null) {
      // we should have a single keyspace and table name combination
      entities = new String[1];
      entities[0] = keyspaces.get(0) + "." + tableName;
    } else if (keyspaceTables != null && !keyspaceTables.isEmpty()) {
      // we should only have a list of keyspace.tables
      entities = keyspaceTables.toArray(new String[keyspaceTables.size()]);
    } else if (keyspaces != null && !keyspaces.isEmpty()) {
      // we have just a list of keyspaces, no tables
      entities = keyspaces.toArray(new String[keyspaces.size()]);
    } else {
      // nothing specified, so snapshot all keyspaces
      List<String> allKeyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
      entities = allKeyspaces.toArray(new String[allKeyspaces.size()]);
    }
    logger.debug("Taking snapshot for entities: {}", Arrays.toString(entities));
    ShimLoader.instance.get().getStorageService().takeSnapshot(snapshotName, optionsMap, entities);
  }

  @Rpc(name = "clearSnapshots")
  public void clearSnapshots(
      @RpcParam(name = "snapshotNames") List<String> snapshotNames,
      @RpcParam(name = "keyspaces") List<String> keyspaces)
      throws IOException {
    // if no snapshotName is specified, use all tags
    if (snapshotNames == null || snapshotNames.isEmpty()) {
      snapshotNames = new ArrayList();
      snapshotNames.addAll(
          ShimLoader.instance.get().getStorageService().getSnapshotDetails().keySet());
    }
    for (String snapshot : snapshotNames) {
      // if no keyspaces are specified, use all keyspaces
      if (keyspaces == null || keyspaces.isEmpty()) {
        keyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
      }
      String[] keyspaceNames = keyspaces.toArray(new String[keyspaces.size()]);
      logger.debug("Deleteing snapshot for tag: {}, keyspaces: {}", snapshot, keyspaceNames);
      ShimLoader.instance.get().getStorageService().clearSnapshot(snapshot, keyspaceNames);
    }
  }

  @Rpc(name = "repair")
  public String repair(
      @RpcParam(name = "keyspaceName") String keyspace,
      @RpcParam(name = "tables") List<String> tables,
      @RpcParam(name = "full") boolean full,
      @RpcParam(name = "notifications") boolean notifications,
      @RpcParam(name = "repairParallelism") String repairParallelism,
      @RpcParam(name = "datacenters") List<String> datacenters,
      @RpcParam(name = "associatedTokens") String ringRangeString,
      @RpcParam(name = "repairThreadCount") Integer repairThreadCount)
      throws IOException {
    // At least one keyspace is required
    assert (keyspace != null);
    Map<String, String> repairSpec = new HashMap<>();
    // add tables/column families
    if (tables != null && !tables.isEmpty()) {
      repairSpec.put(RepairOption.COLUMNFAMILIES_KEY, String.join(",", tables));
    }
    // set incremental reapir
    repairSpec.put(RepairOption.INCREMENTAL_KEY, Boolean.toString(!full));

    // set repair parallelism
    repairSpec.put(RepairOption.PARALLELISM_KEY, repairParallelism);

    // Parallelism should be set if it's requested OR if incremental repair is requested.
    if (!full) {
      // Incremental repair requested, make sure parallelism is correct
      repairSpec.put(RepairOption.PARALLELISM_KEY, RepairParallelism.PARALLEL.getName());
    }

    if (repairThreadCount != null) {
      // if specified, the value should be at least 1
      if (repairThreadCount.compareTo(Integer.valueOf(0)) <= 0) {
        throw new IOException(
            "Invalid repair thread count: "
                + repairThreadCount
                + ". Value should be greater than 0");
      }
      repairSpec.put(RepairOption.JOB_THREADS_KEY, repairThreadCount.toString());
    }
    repairSpec.put(RepairOption.TRACE_KEY, Boolean.toString(Boolean.FALSE));

    if (ringRangeString != null && !ringRangeString.isEmpty()) {
      repairSpec.put(RepairOption.RANGES_KEY, ringRangeString);
    }
    // add datacenters to the repair spec
    if (datacenters != null && !datacenters.isEmpty()) {
      repairSpec.put(RepairOption.DATACENTERS_KEY, String.join(",", datacenters));
    }

    // Since Cassandra provides us with a async, we don't need to use our executor interface for
    // this.
    logger.debug("Starting repair for keyspace: {}", keyspace);
    logger.debug("Repair spec: {}", repairSpec);

    final int repairJobId =
        ShimLoader.instance.get().getStorageService().repairAsync(keyspace, repairSpec);

    if (!notifications) {
      return Integer.valueOf(repairJobId).toString();
    }

    String jobId = String.format("repair-%d", repairJobId);
    final Job job = service.createJob("repair", jobId);

    if (repairJobId == 0) {
      // Job is done and won't continue
      job.setStatusChange(ProgressEventType.COMPLETE, "");
      job.setStatus(Job.JobStatus.COMPLETED);
      job.setFinishedTime(System.currentTimeMillis());
      service.updateJob(job);
    }
    return job.getJobId();
  }

  @Rpc(name = "stopAllRepairs")
  public void stopAllRepairs() {
    ShimLoader.instance.get().getStorageService().forceTerminateAllRepairSessions();
  }

  @Rpc(name = "move")
  public String move(
      @RpcParam(name = "newToken") String newToken, @RpcParam(name = "async") boolean async)
      throws IOException {
    logger.debug("Moving node to new token {}", newToken);

    Runnable moveOperation =
        () -> {
          try {
            ShimLoader.instance.get().getStorageService().move(newToken);
          } catch (IOException e) {
            logger.error("Failed to move node to new token " + newToken, e);
            throw new RuntimeException(e);
          }
        };

    return submitJob("move", moveOperation, async);
  }

  @Rpc(name = "reloadInternodeEncryptionTruststore")
  public void reloadInternodeEncryptionTruststore() throws Exception {
    ShimLoader.instance.get().reloadInternodeEncryptionTruststore();
  }

  @Rpc(name = "getRangeToEndpointMap")
  public Map<List<String>, List<String>> getRangeToEndpointMap(
      @RpcParam(name = "keyspaceName") String keyspaceName) {
    return ShimLoader.instance.get().getStorageService().getRangeToEndpointMap(keyspaceName);
  }
}
