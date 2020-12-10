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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.rpc.Rpc;
import com.datastax.mgmtapi.rpc.RpcParam;
import com.datastax.mgmtapi.rpc.RpcRegistry;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.db.ConsistencyLevel;

/**
 * Replace JMX calls with CQL 'CALL' methods via the the Rpc framework
 */
public class NodeOpsProvider
{
    private static final Logger logger = LoggerFactory.getLogger(NodeOpsProvider.class);
    public static final Supplier<NodeOpsProvider> instance = Suppliers.memoize(() -> new NodeOpsProvider());

    public static final String RPC_CLASS_NAME = "NodeOps";

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
    public void forceKeyspaceCleanup(@RpcParam(name="jobs") int jobs,
            @RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tables") List<String> tables) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Reloading local schema");
        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("NON_LOCAL_STRATEGY"))
        {
            keyspaces = ShimLoader.instance.get().getStorageService().getNonLocalStrategyKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            ShimLoader.instance.get().getStorageService().forceKeyspaceCleanup(jobs, keyspace, tables.toArray(new String[]{}));
        }
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

    @Rpc(name = "createKeyspace")
    public void createKeyspace(@RpcParam(name="keyspaceName") String keyspaceName, @RpcParam(name="replicationSettings") Map<String, Integer> replicationSettings) throws IOException
    {
        logger.debug("Creating keyspace {} with replication settings {}", keyspaceName, replicationSettings);

        ShimLoader.instance.get().processQuery(SchemaBuilder.createKeyspace(keyspaceName)
                        .ifNotExists()
                        .withNetworkTopologyStrategy(replicationSettings)
                        .asCql(),
                ConsistencyLevel.ONE);
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

        ShimLoader.instance.get().processQuery(SchemaBuilder.alterKeyspace(keyspaceName)
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

}
