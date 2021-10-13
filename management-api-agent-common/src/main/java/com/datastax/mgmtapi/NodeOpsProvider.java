/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;


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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;

import com.datastax.mgmtapi.util.Job;
import com.datastax.mgmtapi.util.JobExecutor;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.ListenableFutureTask;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.cassandra.db.compaction.OperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.rpc.Rpc;
import com.datastax.mgmtapi.rpc.RpcParam;
import com.datastax.mgmtapi.rpc.RpcRegistry;
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
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.repair.RepairParallelism;
import org.apache.cassandra.repair.messages.RepairOption;

/**
 * Replace JMX calls with CQL 'CALL' methods via the Rpc framework
 */
public class NodeOpsProvider
{
    private static final Logger logger = LoggerFactory.getLogger(NodeOpsProvider.class);
    public static final Supplier<NodeOpsProvider> instance = Suppliers.memoize(() -> new NodeOpsProvider());
    public static final JobExecutor service = new JobExecutor();

    public static final String RPC_CLASS_NAME = "NodeOps";

    private static final DataTypeCqlNameParser DATA_TYPE_PARSER = new DataTypeCqlNameParser();

    private NodeOpsProvider()
    {

    }

    public synchronized void register()
    {
        RpcRegistry.register(RPC_CLASS_NAME, this);
    }

    public synchronized void unregister()
    {
        RpcRegistry.unregister(RPC_CLASS_NAME);
    }

    @Rpc(name = "jobStatus")
    public Map<String, String> getJobStatus(@RpcParam(name="job_id") String jobId) {
        Map<String, String> resultMap = new HashMap<>();
        Job jobWithId = service.getJobWithId(jobId);
        resultMap.put("id", jobWithId.getJobId());
        resultMap.put("type", jobWithId.getJobType());
        resultMap.put("status", jobWithId.getStatus().name());
        resultMap.put("submit_time", String.valueOf(jobWithId.getSubmitTime()));
        resultMap.put("end_time", String.valueOf(jobWithId.getFinishedTime()));
        if(jobWithId.getStatus() == Job.JobStatus.ERROR) {
            resultMap.put("error", jobWithId.getError().getLocalizedMessage());
        }

        return resultMap;
    }

    @Rpc(name = "setFullQuerylog")
    public void setFullQuerylog(@RpcParam(name="enabled") boolean fullQueryLoggingEnabled) throws UnsupportedOperationException
    {
        logger.debug("Attempting to set full query logging to " + fullQueryLoggingEnabled);
        // Warning: bad design here. Default implementation will throw UnsupportedOperationException at runtime. Comparing strings to get versions is also ugly.
        String cassVersion = ShimLoader.instance.get().getStorageService().getReleaseVersion();
        if (Integer.parseInt(cassVersion.split("\\.")[0]) < 4){
            logger.error("Full query logging is not available in Cassandra < 4x. This call is going to fail.");
        }
        if (fullQueryLoggingEnabled) {
            ShimLoader.instance.get().enableFullQuerylog();
        } else {
            ShimLoader.instance.get().disableFullQuerylog();
        }
    }

    @Rpc(name = "isFullQueryLogEnabled")
    public boolean isFullQueryLogEnabled() throws UnsupportedOperationException
    {
        String cassVersion = ShimLoader.instance.get().getStorageService().getReleaseVersion();
        logger.debug("Attempting to retrieve full query logging status for cassandra version" + cassVersion + ".");
        if (Integer.parseInt(cassVersion.split("\\.")[0]) < 4){
            logger.error("Full query logging is not available in Cassandra < 4x. This call is going to fail.");
        }
        logger.debug("Calling ShimLoader.instance.get().isFullQueryLogEnabled()");
        return ShimLoader.instance.get().isFullQueryLogEnabled();
    }

    @Rpc(name = "reloadSeeds")
    public List<String> reloadSeeds()
    {
        logger.debug("Reloading Seeds");
        Set<InetAddress> seeds = ShimLoader.instance.get().reloadSeeds();
        if (seeds == null)
            throw new RuntimeException("Error reloading seeds");

        return seeds.stream().map(InetAddress::toString).collect(Collectors.toList());
    }


    @Rpc(name = "getReleaseVersion")
    public String getReleaseVersion()
    {
        logger.debug("Getting Release Version");
        return ShimLoader.instance.get().getStorageService().getReleaseVersion();
    }

    @Rpc(name = "decommission")
    public void decommission(@RpcParam(name="force") boolean force) throws InterruptedException
    {
        logger.debug("Decommissioning");
        ShimLoader.instance.get().decommission(force);
    }

    @Rpc(name = "setCompactionThroughput")
    public void setCompactionThroughput(@RpcParam(name="value") int value)
    {
        logger.debug("Setting compaction throughput to {}", value);
        ShimLoader.instance.get().getStorageService().setCompactionThroughputMbPerSec(value);
    }

    @Rpc(name = "assassinate")
    public void assassinate(@RpcParam(name="address") String address) throws UnknownHostException
    {
        logger.debug("Assassinating {}", address);
        ShimLoader.instance.get().getGossiper().assassinateEndpoint(address);
    }

    @Rpc(name = "setLoggingLevel")
    public void setLoggingLevel(@RpcParam(name="classQualifier") String classQualifier,
            @RpcParam(name="level")String level) throws Exception
    {
        logger.debug("Setting logging level of {} to level {}", classQualifier, level);
        ShimLoader.instance.get().getStorageService().setLoggingLevel(classQualifier, level);
    }

    @Rpc(name = "drain")
    public void drain() throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Draining");
        ShimLoader.instance.get().getStorageService().drain();
    }

    @Rpc(name = "truncateAllHints")
    public void truncateHints()
    {
        logger.debug("Truncating all hints");
        ShimLoader.instance.get().getHintsService().deleteAllHints();
    }

    @Rpc(name = "truncateHintsForHost")
    public void truncateHints(@RpcParam(name="host") String host)
    {
        logger.debug("Truncating hints for host {}", host);
        ShimLoader.instance.get().getHintsService().deleteAllHintsForEndpoint(host);
    }

    @Rpc(name = "resetLocalSchema")
    public void resetLocalSchema() throws IOException
    {
        logger.debug("Resetting local schema");
        ShimLoader.instance.get().getStorageService().resetLocalSchema();
    }

    @Rpc(name = "reloadLocalSchema")
    public void reloadLocalSchema()
    {
        logger.debug("Reloading local schema");
        ShimLoader.instance.get().getStorageService().reloadLocalSchema();
    }

    @Rpc(name = "upgradeSSTables")
    public void upgradeSSTables(@RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="excludeCurrentVersion" ) boolean excludeCurrentVersion,
            @RpcParam(name="jobs") int jobs,
            @RpcParam(name="tableNames") List<String> tableNames) throws IOException, ExecutionException, InterruptedException
    {
        logger.debug("Upgrading SSTables");

        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL"))
        {
            keyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            ShimLoader.instance.get().getStorageService().upgradeSSTables(keyspace, excludeCurrentVersion, jobs, tableNames.toArray(new String[]{}));
        }
    }

    @Rpc(name = "forceKeyspaceCleanup")
    public String forceKeyspaceCleanup(@RpcParam(name="jobs") int jobs,
            @RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tables") List<String> tables) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Reloading local schema");
        final List<String> keyspaces = new ArrayList<>();

        if (keyspaceName != null && keyspaceName.equalsIgnoreCase("NON_LOCAL_STRATEGY"))
        {
            keyspaces.addAll(ShimLoader.instance.get().getStorageService().getNonLocalStrategyKeyspaces());
        } else {
            keyspaces.add(keyspaceName);
        }

        // Send to background execution
        return service.submit(OperationType.CLEANUP.name(), () -> {
            for (String keyspace : keyspaces) {
                try {
                    ShimLoader.instance.get().getStorageService().forceKeyspaceCleanup(jobs, keyspace, tables.toArray(new String[]{}));
                } catch (IOException | ExecutionException | InterruptedException e) {
                    logger.error("Failed to execute forceKeyspaceCleanup in " + keyspace, e);
                }
            }
        });
    }

    @Rpc(name = "forceKeyspaceCompactionForTokenRange")
    public void forceKeyspaceCompactionForTokenRange(@RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="startToken") String startToken,
            @RpcParam(name="endToken") String endToken,
            @RpcParam(name="tableNames") List<String> tableNames) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Forcing keyspace compaction for token range on keyspace {}", keyspaceName);

        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL"))
        {
            keyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            ShimLoader.instance.get().getStorageService().forceKeyspaceCompactionForTokenRange(keyspace, startToken, endToken, tableNames.toArray(new String[]{}));
        }
    }

    @Rpc(name = "forceKeyspaceCompaction")
    public void forceKeyspaceCompaction(@RpcParam(name="splitOutput") boolean splitOutput,
            @RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tableNames") List<String> tableNames) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Forcing keyspace compaction on keyspace {}", keyspaceName);

        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL"))
        {
            keyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            ShimLoader.instance.get().getStorageService().forceKeyspaceCompaction(splitOutput, keyspace, tableNames.toArray(new String[]{}));
        }
    }

    @Rpc(name = "garbageCollect")
    public void garbageCollect(@RpcParam(name="tombstoneOption") String tombstoneOption,
            @RpcParam(name="jobs") int jobs,
            @RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tableNames") List<String> tableNames) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Garbage collecting on keyspace {}", keyspaceName);
        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL"))
        {
            keyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            ShimLoader.instance.get().getStorageService().garbageCollect(tombstoneOption, jobs, keyspace, tableNames.toArray(new String[]{}));
        }
    }

    @Rpc(name = "loadNewSSTables")
    public void loadNewSSTables(@RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="table") String table)
    {
        logger.debug("Forcing keyspace refresh on keyspace {} and table {}", keyspaceName, table);
        ShimLoader.instance.get().getStorageService().loadNewSSTables(keyspaceName, table);
    }

    @Rpc(name = "forceKeyspaceFlush")
    public void forceKeyspaceFlush(@RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tableNames") List<String> tableNames) throws IOException
    {
        logger.debug("Forcing keyspace flush on keyspace {}", keyspaceName);
        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL"))
        {
            keyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            ShimLoader.instance.get().getStorageService().forceKeyspaceFlush(keyspace, tableNames.toArray(new String[]{}));
        }
    }

    @Rpc(name = "scrub")
    public void scrub(@RpcParam(name="disableSnapshot") boolean disableSnapshot,
            @RpcParam(name="skipCorrupted") boolean skipCorrupted,
            @RpcParam(name="checkData") boolean checkData,
            @RpcParam(name="reinsertOverflowedTTL") boolean reinsertOverflowedTTL,
            @RpcParam(name="jobs") int jobs,
            @RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tables") List<String> tables) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Scrubbing tables on keyspace {}", keyspaceName);
        ShimLoader.instance.get().getStorageService().scrub(disableSnapshot, skipCorrupted, checkData, reinsertOverflowedTTL, jobs, keyspaceName, tables.toArray(new String[]{}));
    }

    @Rpc(name = "forceUserDefinedCompaction")
    public void forceUserDefinedCompaction(@RpcParam(name="datafiles") String datafiles)
    {
        logger.debug("Forcing user defined compaction");
        ShimLoader.instance.get().getCompactionManager().forceUserDefinedCompaction(datafiles);
    }

    @Rpc(name = "createRole")
    public void createRole(@RpcParam(name="username") String username,
            @RpcParam(name = "superuser") Boolean superUser,
            @RpcParam(name = "login") Boolean login,
            @RpcParam(name = "password") String password)
    {
        logger.debug("Creating role {}", username);
        RoleResource rr = RoleResource.role(username);
        RoleOptions ro = new RoleOptions();
        ro.setOption(IRoleManager.Option.SUPERUSER, superUser);
        ro.setOption(IRoleManager.Option.LOGIN, login);
        ro.setOption(IRoleManager.Option.PASSWORD, password);

        ShimLoader.instance.get().getRoleManager().createRole(AuthenticatedUser.SYSTEM_USER, rr, ro);
    }

    @Rpc(name = "checkConsistencyLevel")
    public Map<List<Long>, List<String>> checkConsistencyLevel(@RpcParam(name="consistency_level") String consistencyLevelName,
            @RpcParam(name="rf_per_dc") Integer rfPerDc)
    {
        logger.debug("Checking cl={} assuming {} replicas per node", consistencyLevelName, rfPerDc);
        Preconditions.checkArgument(consistencyLevelName != null, "consistency_level must be defined");
        Preconditions.checkArgument(rfPerDc != null, "rf_per_dc must be defined");
        Preconditions.checkArgument(rfPerDc > 0, "rf_per_dc must be > 0");

        return ShimLoader.instance.get().checkConsistencyLevel(consistencyLevelName, rfPerDc);
    }

    @Rpc(name = "getEndpointStates")
    public List<Map<String,String>> getEndpointStates()
    {
        return ShimLoader.instance.get().getEndpointStates();
    }

    @Rpc(name = "getStreamInfo")
    public List<Map<String, List<Map<String, String>>>> getStreamInfo()
    {
        return ShimLoader.instance.get().getStreamInfo();
    }

    @Rpc(name = "getKeyspaces")
    public List<String> getKeyspaces()
    {
        return ShimLoader.instance.get().getKeyspaces();
    }

    @Rpc(name = "getReplication")
    public Map<String, String> getReplication(@RpcParam(name = "keyspaceName") String keyspaceName)
    {
        String query = QueryBuilder.selectFrom("system_schema", "keyspaces")
                                   .column("replication")
                                   .where(Relation.column("keyspace_name").isEqualTo(QueryBuilder.literal(keyspaceName))).asCql();
        UntypedResultSet rows = ShimLoader.instance.get().processQuery(query, ConsistencyLevel.ONE);
        if (rows.isEmpty())
        {
            return null;
        }
        return rows.one().getMap("replication", UTF8Type.instance, UTF8Type.instance);
    }

    @Rpc(name = "getTables")
    public List<String> getTables(@RpcParam(name = "keyspaceName") String keyspaceName)
    {
        String query = QueryBuilder.selectFrom("system_schema", "tables")
                                   .column("table_name")
                                   .where(Relation.column("keyspace_name").isEqualTo(QueryBuilder.literal(keyspaceName))).asCql();
        UntypedResultSet rows = ShimLoader.instance.get().processQuery(query, ConsistencyLevel.ONE);
        List<String> tables = new ArrayList<>();
        for (UntypedResultSet.Row row : rows)
        {
            tables.add(row.getString("table_name"));
        }
        return tables;
    }

    @Rpc(name = "createKeyspace")
    public void createKeyspace(@RpcParam(name="keyspaceName") String keyspaceName, @RpcParam(name="replicationSettings") Map<String, Integer> replicationSettings) throws IOException
    {
        logger.debug("Creating keyspace {} with replication settings {}", keyspaceName, replicationSettings);

        ShimLoader.instance.get().processQuery(SchemaBuilder.createKeyspace(CqlIdentifier.fromInternal(keyspaceName))
                        .ifNotExists()
                        .withNetworkTopologyStrategy(replicationSettings)
                        .asCql(),
                ConsistencyLevel.ONE);
    }

    @Rpc(name = "createTable")
    public void createTable(@RpcParam(name = "keyspaceName") String keyspaceName,
                            @RpcParam(name = "tableName") String tableName,
                            @RpcParam(name = "columnsAndTypes") Map<String, String> columnsAndTypes,
                            @RpcParam(name = "partitionKeyColumnNames") List<String> partitionKeyColumnNames,
                            @RpcParam(name = "clusteringColumnNames") List<String> clusteringColumnNames,
                            @RpcParam(name = "clusteringOrders") List<String> clusteringOrders,
                            @RpcParam(name = "staticColumnNames") List<String> staticColumnNames,
                            @RpcParam(name = "simpleOptions") Map<String, String> simpleOptions,
                            @RpcParam(name = "complexOptions") Map<String, Map<String, String>> complexOptions)
    {
        logger.debug("Creating table {}", tableName);
        CqlIdentifier keyspaceId = CqlIdentifier.fromInternal(keyspaceName);
        CqlIdentifier tableId = CqlIdentifier.fromInternal(tableName);
        OngoingPartitionKey stmtStart = SchemaBuilder.createTable(keyspaceId, tableId).ifNotExists();
        for (String name : partitionKeyColumnNames)
        {
            DataType dt = DATA_TYPE_PARSER.parse(keyspaceId, columnsAndTypes.get(name), null, null);
            stmtStart = stmtStart.withPartitionKey(CqlIdentifier.fromInternal(name), dt);
        }
        CreateTable stmt = (CreateTable) stmtStart;
        for (String name : clusteringColumnNames)
        {
            DataType dt = DATA_TYPE_PARSER.parse(keyspaceId, columnsAndTypes.get(name), null, null);
            stmt = stmt.withClusteringColumn(CqlIdentifier.fromInternal(name), dt);
        }
        for (String name : staticColumnNames)
        {
            DataType dt = DATA_TYPE_PARSER.parse(keyspaceId, columnsAndTypes.get(name), null, null);
            stmt = stmt.withStaticColumn(CqlIdentifier.fromInternal(name), dt);
        }
        for (String name : columnsAndTypes.keySet())
        {
            if (!partitionKeyColumnNames.contains(name) && !clusteringColumnNames.contains(name) && !staticColumnNames.contains(name))
            {
                DataType dt = DATA_TYPE_PARSER.parse(keyspaceId, columnsAndTypes.get(name), null, null);
                stmt = stmt.withColumn(CqlIdentifier.fromInternal(name), dt);
            }
        }
        CreateTableWithOptions stmtFinal = stmt;
        for (int i = 0; i < clusteringColumnNames.size(); i++)
        {
            String name = clusteringColumnNames.get(i);
            ClusteringOrder order = ClusteringOrder.valueOf(clusteringOrders.get(i).toUpperCase(Locale.ROOT));
            stmtFinal = stmtFinal.withClusteringOrder(CqlIdentifier.fromInternal(name), order);
        }
        for (Map.Entry<String, String> entry : simpleOptions.entrySet())
        {
            stmtFinal = stmtFinal.withOption(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Map<String, String>> entry : complexOptions.entrySet())
        {
            stmtFinal = stmtFinal.withOption(entry.getKey(), entry.getValue());
        }
        String query = stmtFinal.asCql();
        logger.debug("Generated query: {}", query);
        ShimLoader.instance.get().processQuery(query, ConsistencyLevel.ONE);
        logger.debug("Table successfully created: {}", tableId);
    }

    @Rpc(name = "getLocalDataCenter")
    public String getLocalDataCenter()
    {
        return ShimLoader.instance.get().getLocalDataCenter();
    }

    @Rpc(name = "alterKeyspace")
    public void alterKeyspace(@RpcParam(name="keyspaceName") String keyspaceName, @RpcParam(name="replicationSettings") Map<String, Integer> replicationSettings) throws IOException
    {
        logger.debug("Creating keyspace {} with replication settings {}", keyspaceName, replicationSettings);

        ShimLoader.instance.get().processQuery(SchemaBuilder.alterKeyspace(CqlIdentifier.fromInternal(keyspaceName))
                        .withNetworkTopologyStrategy(replicationSettings)
                        .asCql(),
                ConsistencyLevel.ONE);
    }

    @Rpc(name = "getSnapshotDetails")
    public List<Map<String, String>> getSnapshotDetails(@RpcParam(name="snapshotNames") List<String> snapshotNames, @RpcParam(name="keyspaces") List<String> keyspaces)
    {
        logger.debug("Fetching snapshots with snapshot names {} and keyspaces {}", snapshotNames, keyspaces);
        List<Map<String, String>> detailsList = new ArrayList<>();
        // get the map of snapshots
        Map<String, TabularData> snapshots = ShimLoader.instance.get().getStorageService().getSnapshotDetails();
        for (Map.Entry<String, TabularData> entry : snapshots.entrySet())
        {
            // create the map of data per snapshot name
            String snapshotTag = entry.getKey();
            if (snapshotNames == null || snapshotNames.isEmpty() || snapshotNames.contains(snapshotTag))
            {
                TabularData tabularData = entry.getValue();
                for (CompositeDataSupport compositeData : (Collection<CompositeDataSupport>)(tabularData.values()))
                {
                    String keyspaceName = compositeData.get("Keyspace name").toString();
                    if (keyspaces == null || keyspaces.isEmpty() || keyspaces.contains(keyspaceName))
                    {
                        Map<String, String> detailsMap = new HashMap<>();
                        for (String itemName : compositeData.getCompositeType().keySet())
                        {
                            String value = compositeData.get(itemName).toString();
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
            @RpcParam(name="snapshotName") String snapshotName,
            @RpcParam(name="keyspaces") List<String> keyspaces,
            @RpcParam(name="tableName") String tableName,
            @RpcParam(name="skipFlush") Boolean skipFlush,
            @RpcParam(name="keyspaceTables") List<String> keyspaceTables) throws IOException
    {
        // skipFlush options map
        Map<String, String> optionsMap = new HashMap<>();
        optionsMap.put("skipFlush", skipFlush.toString());

        // build entities array
        String[] entities = null;
        if (tableName != null)
        {
            // we should have a single keyspace and table name combination
            entities = new String[1];
            entities[0] = keyspaces.get(0) + "." + tableName;
        }
        else if (keyspaceTables != null && !keyspaceTables.isEmpty())
        {
            // we should only have a list of keyspace.tables
            entities = keyspaceTables.toArray(new String[keyspaceTables.size()]);
        }
        else if (keyspaces != null && !keyspaces.isEmpty())
        {
            // we have just a list of keyspaces, no tables
            entities = keyspaces.toArray(new String[keyspaces.size()]);
        }
        else
        {
            // nothing specified, so snapshot all keyspaces
            List<String> allKeyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
            entities = allKeyspaces.toArray(new String[allKeyspaces.size()]);
        }
        logger.debug("Taking snapshot for entities: {}", Arrays.toString(entities));
        ShimLoader.instance.get().getStorageService().takeSnapshot(snapshotName, optionsMap, entities);
    }

    @Rpc(name = "clearSnapshots")
    public void clearSnapshots(@RpcParam(name="snapshotNames") List<String> snapshotNames, @RpcParam(name="keyspaces") List<String> keyspaces) throws IOException
    {
        // if no snapshotName is specified, use all tags
        if (snapshotNames == null || snapshotNames.isEmpty())
        {
            snapshotNames = new ArrayList();
            snapshotNames.addAll(ShimLoader.instance.get().getStorageService().getSnapshotDetails().keySet());
        }
        for (String snapshot : snapshotNames)
        {
            // if no keyspaces are specified, use all keyspaces
            if (keyspaces == null || keyspaces.isEmpty())
            {
                keyspaces = ShimLoader.instance.get().getStorageService().getKeyspaces();
            }
            String[] keyspaceNames = keyspaces.toArray(new String[keyspaces.size()]);
            logger.debug("Deleteing snapshot for tag: {}, keyspaces: {}", snapshot, keyspaceNames);
            ShimLoader.instance.get().getStorageService().clearSnapshot(snapshot, keyspaceNames);
        }
    }

    @Rpc(name = "repair")
    public void repair(@RpcParam(name="keyspaceName") String keyspace, @RpcParam(name="tables") List<String> tables, @RpcParam(name="full") Boolean full) throws IOException
    {
        // At least one keyspace is required
        if (keyspace != null)
        {
            // create the repair spec
            Map<String, String> repairSpec = new HashMap<>();

            // add any specified tables to the repair spec
            if (tables != null && !tables.isEmpty())
            {
                // set the tables/column families
                repairSpec.put(RepairOption.COLUMNFAMILIES_KEY, String.join(",", tables));
            }

            // handle incremental vs full
            boolean isIncremental = Boolean.FALSE.equals(full);
            repairSpec.put(RepairOption.INCREMENTAL_KEY, Boolean.toString(isIncremental));
            if (isIncremental)
            {
                // incremental repairs will fail if parallelism is not set
                repairSpec.put(RepairOption.PARALLELISM_KEY, RepairParallelism.PARALLEL.getName());
            }
            ShimLoader.instance.get().getStorageService().repairAsync(keyspace, repairSpec);
        }
    }
}
