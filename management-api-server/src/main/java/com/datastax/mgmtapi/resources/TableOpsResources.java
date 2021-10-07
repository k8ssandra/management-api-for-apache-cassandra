/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.datastax.mgmtapi.resources.models.CompactRequest;
import com.datastax.mgmtapi.resources.models.CreateTableRequest;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import com.datastax.mgmtapi.resources.models.ScrubRequest;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import io.swagger.v3.oas.annotations.Operation;

import static com.datastax.mgmtapi.resources.NodeOpsResources.handle;

@Path("/api/v0/ops/tables")
public class TableOpsResources
{
    private static final Logger logger = LoggerFactory.getLogger(TableOpsResources.class);

    private final ManagementApplication app;
    private final CqlService cqlService;

    public TableOpsResources(ManagementApplication application) {
        this.app = application;
        this.cqlService = application.cqlService;
    }

    @POST
    @Path("/scrub")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Scrub (rebuild sstables for) one or more tables")
    public Response scrub(ScrubRequest scrubRequest)
    {
        return handle(() ->
        {
            List<String> tables = scrubRequest.tables;
            if (CollectionUtils.isEmpty(tables)) {
                tables = new ArrayList<>();
            }

            String keyspaceName = scrubRequest.keyspaceName;
            if (StringUtils.isBlank(keyspaceName))
            {
                keyspaceName = "ALL";
            }

            cqlService.executePreparedStatement(app.dbUnixSocketFile,
                    "CALL NodeOps.scrub(?, ?, ?, ?, ?, ?, ?)", scrubRequest.disableSnapshot, scrubRequest.skipCorrupted,
                    scrubRequest.checkData, scrubRequest.reinsertOverflowedTTL, scrubRequest.jobs, keyspaceName, tables);

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/sstables/upgrade")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Rewrite sstables (for the requested tables) that are not on the current version (thus upgrading them to said current version)")
    public Response upgradeSSTables(@QueryParam(value="excludeCurrentVersion")boolean excludeCurrentVersion, KeyspaceRequest keyspaceRequest)
    {
        return handle(() ->
        {
            List<String> tables = keyspaceRequest.tables;
            if (CollectionUtils.isEmpty(tables)) {
                tables = new ArrayList<>();
            }

            String keyspaceName = keyspaceRequest.keyspaceName;
            if (StringUtils.isBlank(keyspaceName))
            {
                keyspaceName = "ALL";
            }

            cqlService.executePreparedStatement(app.dbUnixSocketFile,
                    "CALL NodeOps.upgradeSSTables(?, ?, ?, ?)", keyspaceName, !excludeCurrentVersion, keyspaceRequest.jobs, tables);

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/compact")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Force a (major) compaction on one or more tables or user-defined compaction on given SSTables")
    public Response compact(CompactRequest compactRequest)
    {
        return handle(() ->
        {
            // Retaining logic from org.apache.cassandra.tools.nodetool.Compact
            boolean tokenProvided = !(StringUtils.isBlank(compactRequest.startToken) && StringUtils.isBlank(compactRequest.endToken));
            if (compactRequest.splitOutput && (compactRequest.userDefined || tokenProvided)) {
                return Response.status(HttpStatus.SC_BAD_REQUEST).entity("Invalid option combination: Can not use split-output here").build();
            }
            if (compactRequest.userDefined && tokenProvided) {
                return Response.status(HttpStatus.SC_BAD_REQUEST).entity("Invalid option combination: Can not provide tokens when using user-defined").build();
            }

            if (compactRequest.userDefined)
            {
                try
                {
                    if (CollectionUtils.isEmpty(compactRequest.userDefinedFiles))
                    {
                        return Response.status(HttpStatus.SC_BAD_REQUEST).entity("Must provide a file if setting userDefined to true").build();
                    }

                    String userDefinedFiles = String.join(",", compactRequest.userDefinedFiles);
                    cqlService.executePreparedStatement(app.dbUnixSocketFile,
                            "CALL NodeOps.forceUserDefinedCompaction(?)", userDefinedFiles);
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred during user defined compaction", e);
                }
                return Response.ok("OK").build();
            }

            List<String> tables = compactRequest.tables;
            if (CollectionUtils.isEmpty(tables)) {
                tables = new ArrayList<>();
            }

            String keyspaceName = compactRequest.keyspaceName;
            if (StringUtils.isBlank(keyspaceName))
            {
                keyspaceName = "ALL";
            }

            if (tokenProvided)
            {
                cqlService.executePreparedStatement(app.dbUnixSocketFile,
                        "CALL NodeOps.forceKeyspaceCompactionForTokenRange(?, ?, ?, ?)", keyspaceName,
                        compactRequest.startToken, compactRequest.endToken, tables);
            }
            else
            {
                cqlService.executePreparedStatement(app.dbUnixSocketFile,
                        "CALL NodeOps.forceKeyspaceCompaction(?, ?, ?)", compactRequest.splitOutput, keyspaceName,
                        compactRequest.tables);
            }

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/garbagecollect")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Remove deleted data from one or more tables")
    public Response garbageCollect(@QueryParam(value="tombstoneOption")String tombstoneOptionStr, KeyspaceRequest keyspaceRequest)
    {
        return handle(() ->
        {
            List<String> tables = keyspaceRequest.tables;
            if (CollectionUtils.isEmpty(tables)) {
                tables = new ArrayList<>();
            }

            String keyspaceName = keyspaceRequest.keyspaceName;
            if (StringUtils.isBlank(keyspaceName))
            {
                keyspaceName = "ALL";
            }
            String tombstoneOption = tombstoneOptionStr;
            if (StringUtils.isBlank(tombstoneOption))
            {
                tombstoneOption = "ROW";
            }

            if (!StringUtils.equalsIgnoreCase("ROW", tombstoneOption) && !StringUtils.equalsIgnoreCase("CELL", tombstoneOption))
            {
                return Response.status(HttpStatus.SC_BAD_REQUEST).entity("tombstoneOption must be either ROW or CELL").build();
            }

            cqlService.executePreparedStatement(app.dbUnixSocketFile,
                    "CALL NodeOps.garbageCollect(?, ?, ?, ?)", tombstoneOption, keyspaceRequest.jobs, keyspaceName, tables);

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/flush")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Flush one or more tables")
    public Response flush(KeyspaceRequest keyspaceRequest)
    {
        return handle(() ->
        {
            List<String> tables = keyspaceRequest.tables;
            if (CollectionUtils.isEmpty(tables)) {
                tables = new ArrayList<>();
            }

            String keyspaceName = keyspaceRequest.keyspaceName;
            if (StringUtils.isBlank(keyspaceName))
            {
                keyspaceName = "ALL";
            }

            cqlService.executePreparedStatement(app.dbUnixSocketFile,
                    "CALL NodeOps.forceKeyspaceFlush(?, ?)", keyspaceName, tables);

            return Response.ok("OK").build();
        });
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "List the table names in the given keyspace")
    public Response list(@QueryParam(value="keyspaceName")String keyspaceName)
    {
        if (StringUtils.isBlank(keyspaceName))
        {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                    .entity("List tables failed. Non-empty 'keyspaceName' must be provided").build();
        }
        return NodeOpsResources.handle(() ->
        {
            ResultSet result = cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.getTables(?)", keyspaceName);
            Row row = result.one();
            assert row != null;
            List<String> tables = row.getList(0, String.class);
            return Response.ok(tables, MediaType.APPLICATION_JSON).build();
        });
    }

    @POST
    @Path("/create")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a new table")
    public Response create(CreateTableRequest request)
    {
        try {
            request.validate();
        } catch (RuntimeException e) {
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                    .entity("Table creation failed: " + e.getMessage()).build();
        }
        return NodeOpsResources.handle(() ->
        {

            cqlService.executePreparedStatement(
            app.dbUnixSocketFile,
            "CALL NodeOps.createTable(?, ?, ?, ?, ?, ?, ?, ?, ?)",
            request.keyspaceName,
            request.tableName,
            request.columnNamesAndTypes(),
            request.partitionKeyColumnNames(),
            request.clusteringColumnNames(),
            request.clusteringOrders(),
            request.staticColumnNames(),
            request.simpleOptions(),
            request.complexOptions());

            return Response.ok("OK").build();
        });
    }

}
