package com.datastax.mgmtapi;


import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.datastax.mgmtapi.rpc.Rpc;
import com.datastax.mgmtapi.rpc.RpcParam;
import com.datastax.mgmtapi.rpc.RpcRegistry;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.CassandraRoleManager;
import org.apache.cassandra.auth.IRoleManager;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleOptions;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.HintedHandOffManager;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.compaction.CompactionManager;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.gms.GossiperInterceptor;
import org.apache.cassandra.locator.AbstractReplicationStrategy;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.locator.NetworkTopologyStrategy;
import org.apache.cassandra.locator.TokenMetadata;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.KeyspaceParams;
import org.apache.cassandra.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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

    @Rpc(name = "reloadSeeds", permission = Permission.EXECUTE)
    public List<String> reloadSeeds()
    {
        logger.debug("Reloading Seeds");
        Set<InetAddress> seeds = ShimLoader.instance.get().reloadSeeds();
        if (seeds == null)
            throw new RuntimeException("Error reloading seeds");

        return seeds.stream().map(InetAddress::toString).collect(Collectors.toList());
    }


    @Rpc(name = "getReleaseVersion", permission = Permission.EXECUTE)
    public String getReleaseVersion()
    {
        logger.debug("Getting Release Version");
        return StorageService.instance.getReleaseVersion();
    }

    @Rpc(name = "decommission", permission = Permission.EXECUTE)
    public void decommission(@RpcParam(name="force") boolean force) throws InterruptedException
    {
        logger.debug("Decommissioning");
        ShimLoader.instance.get().decommission(force);
    }

    @Rpc(name = "setCompactionThroughput", permission = Permission.EXECUTE)
    public void setCompactionThroughput(@RpcParam(name="value") int value)
    {
        logger.debug("Setting compaction throughput to {}", value);
        StorageService.instance.setCompactionThroughputMbPerSec(value);
    }

    @Rpc(name = "assassinate", permission = Permission.EXECUTE)
    public void assassinate(@RpcParam(name="address") String address) throws UnknownHostException
    {
        logger.debug("Assassinating {}", address);
        Gossiper.instance.assassinateEndpoint(address);
    }

    @Rpc(name = "setLoggingLevel", permission = Permission.EXECUTE)
    public void setLoggingLevel(@RpcParam(name="classQualifier") String classQualifier,
            @RpcParam(name="level")String level) throws Exception
    {
        logger.debug("Setting logging level of {} to level {}", classQualifier, level);
        StorageService.instance.setLoggingLevel(classQualifier, level);
    }

    @Rpc(name = "drain", permission = Permission.EXECUTE)
    public void drain() throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Draining");
        StorageService.instance.drain();
    }

    @Rpc(name = "truncateAllHints", permission = Permission.EXECUTE)
    public void truncateHints()
    {
        logger.debug("Truncating all hints");
        HintedHandOffManager.instance.truncateAllHints();
    }

    @Rpc(name = "truncateHintsForHost", permission = Permission.EXECUTE)
    public void truncateHints(@RpcParam(name="host") String host)
    {
        logger.debug("Truncating hints for host {}", host);
        HintedHandOffManager.instance.deleteHintsForEndpoint(host);
    }

    @Rpc(name = "resetLocalSchema", permission = Permission.EXECUTE)
    public void resetLocalSchema() throws IOException
    {
        logger.debug("Resetting local schema");
        StorageService.instance.resetLocalSchema();
    }

    @Rpc(name = "reloadLocalSchema", permission = Permission.EXECUTE)
    public void reloadLocalSchema()
    {
        logger.debug("Reloading local schema");
        StorageService.instance.reloadLocalSchema();
    }

    @Rpc(name = "upgradeSSTables", permission = Permission.EXECUTE)
    public void upgradeSSTables(@RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="excludeCurrentVersion" ) boolean excludeCurrentVersion,
            @RpcParam(name="jobs") int jobs,
            @RpcParam(name="tableNames") List<String> tableNames) throws IOException, ExecutionException, InterruptedException
    {
        logger.debug("Upgrading SSTables");

        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL"))
        {
            keyspaces = StorageService.instance.getKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            StorageService.instance.upgradeSSTables(keyspace, excludeCurrentVersion, jobs, tableNames.toArray(new String[]{}));
        }
    }

    @Rpc(name = "forceKeyspaceCleanup", permission = Permission.EXECUTE)
    public void forceKeyspaceCleanup(@RpcParam(name="jobs") int jobs,
            @RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tables") List<String> tables) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Reloading local schema");
        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("NON_LOCAL_STRATEGY"))
        {
            keyspaces = StorageService.instance.getNonLocalStrategyKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            StorageService.instance.forceKeyspaceCleanup(jobs, keyspace, tables.toArray(new String[]{}));
        }
    }

    @Rpc(name = "forceKeyspaceCompactionForTokenRange", permission = Permission.EXECUTE)
    public void forceKeyspaceCompactionForTokenRange(@RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="startToken") String startToken,
            @RpcParam(name="endToken") String endToken,
            @RpcParam(name="tableNames") List<String> tableNames) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Forcing keyspace compaction for token range on keyspace {}", keyspaceName);

        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL"))
        {
            keyspaces = StorageService.instance.getKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            StorageService.instance.forceKeyspaceCompactionForTokenRange(keyspace, startToken, endToken, tableNames.toArray(new String[]{}));
        }
    }

    @Rpc(name = "forceKeyspaceCompaction", permission = Permission.EXECUTE)
    public void forceKeyspaceCompaction(@RpcParam(name="splitOutput") boolean splitOutput,
            @RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tableNames") List<String> tableNames) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Forcing keyspace compaction on keyspace {}", keyspaceName);

        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL"))
        {
            keyspaces = StorageService.instance.getKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            StorageService.instance.forceKeyspaceCompaction(splitOutput, keyspace, tableNames.toArray(new String[]{}));
        }
    }

    @Rpc(name = "garbageCollect", permission = Permission.EXECUTE)
    public void garbageCollect(@RpcParam(name="tombstoneOption") String tombstoneOption,
            @RpcParam(name="jobs") int jobs,
            @RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tableNames") List<String> tableNames) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Garbage collecting on keyspace {}", keyspaceName);
        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL"))
        {
            keyspaces = StorageService.instance.getKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            StorageService.instance.garbageCollect(tombstoneOption, jobs, keyspace, tableNames.toArray(new String[]{}));
        }
    }

    @Rpc(name = "loadNewSSTables", permission = Permission.EXECUTE)
    public void loadNewSSTables(@RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="table") String table)
    {
        logger.debug("Forcing keyspace refresh on keyspace {} and table {}", keyspaceName, table);
        StorageService.instance.loadNewSSTables(keyspaceName, table);
    }

    @Rpc(name = "forceKeyspaceFlush", permission = Permission.EXECUTE)
    public void forceKeyspaceFlush(@RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tableNames") List<String> tableNames) throws IOException
    {
        logger.debug("Forcing keyspace flush on keyspace {}", keyspaceName);
        List<String> keyspaces = Collections.singletonList(keyspaceName);
        if (keyspaceName != null && keyspaceName.toUpperCase().equals("ALL"))
        {
            keyspaces = StorageService.instance.getKeyspaces();
        }

        for (String keyspace : keyspaces)
        {
            StorageService.instance.forceKeyspaceFlush(keyspace, tableNames.toArray(new String[]{}));
        }
    }

    @Rpc(name = "scrub", permission = Permission.EXECUTE)
    public void scrub(@RpcParam(name="disableSnapshot") boolean disableSnapshot,
            @RpcParam(name="skipCorrupted") boolean skipCorrupted,
            @RpcParam(name="checkData") boolean checkData,
            @RpcParam(name="reinsertOverflowedTTL") boolean reinsertOverflowedTTL,
            @RpcParam(name="jobs") int jobs,
            @RpcParam(name="keyspaceName") String keyspaceName,
            @RpcParam(name="tables") List<String> tables) throws InterruptedException, ExecutionException, IOException
    {
        logger.debug("Scrubbing tables on keyspace {}", keyspaceName);
        StorageService.instance.scrub(disableSnapshot, skipCorrupted, checkData, reinsertOverflowedTTL, jobs, keyspaceName, tables.toArray(new String[]{}));
    }

    @Rpc(name = "forceUserDefinedCompaction", permission = Permission.EXECUTE)
    public void forceUserDefinedCompaction(@RpcParam(name="datafiles") String datafiles)
    {
        logger.debug("Forcing user defined compaction");
        CompactionManager.instance.forceUserDefinedCompaction(datafiles);
    }

    @Rpc(name = "createRole", permission = Permission.AUTHORIZE)
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
        
        DatabaseDescriptor.getRoleManager().createRole(AuthenticatedUser.SYSTEM_USER, rr, ro);
    }

    @Rpc(name = "checkConsistencyLevel", permission = Permission.EXECUTE)
    public Map<List<Long>, List<String>> checkConsistencyLevel(@RpcParam(name="consistency_level") String consistencyLevelName,
            @RpcParam(name="rf_per_dc") Integer rfPerDc)
    {
        logger.debug("Checking cl={} assuming {} replicas per node", consistencyLevelName, rfPerDc);
        Preconditions.checkArgument(consistencyLevelName != null, "consistency_level must be defined");
        Preconditions.checkArgument(rfPerDc != null, "rf_per_dc must be defined");
        Preconditions.checkArgument(rfPerDc > 0, "rf_per_dc must be > 0");

        return ShimLoader.instance.get().checkConsistencyLevel(consistencyLevelName, rfPerDc);
    }

    @Rpc(name = "getEndpointStates", permission = Permission.EXECUTE)
    public List<Map<String,String>> getEndpointStates()
    {
        return ShimLoader.instance.get().getEndpointStates();
    }

}
