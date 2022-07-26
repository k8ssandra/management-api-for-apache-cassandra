package com.datastax.mgmtapi.resources.v1;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.helpers.ResponseTools;
import com.datastax.mgmtapi.resources.models.CompactRequest;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import com.datastax.mgmtapi.resources.models.ScrubRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static com.datastax.mgmtapi.resources.NodeOpsResources.handle;

@Path("/api/v1/ops/tables")
public class TableOpsResources {
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
    @ApiResponse(responseCode = "202", description = "Job ID for table scrub process",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(
                            implementation = String.class
                    ),
                    examples = @ExampleObject(
                            value = "d69d1d95-9348-4460-95d2-ae342870fade"
                    )
            )
    )
    @Operation(summary = "Scrub (rebuild sstables for) one or more tables. This operation is asynchronous and returns immediately.", operationId = "scrub")
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

            return Response.accepted(ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService,
                    "CALL NodeOps.scrub(?, ?, ?, ?, ?, ?, ?, ?)", scrubRequest.disableSnapshot, scrubRequest.skipCorrupted,
                    scrubRequest.checkData, scrubRequest.reinsertOverflowedTTL, scrubRequest.jobs, keyspaceName, tables, true)).build();
        });
    }

    @POST
    @Path("/sstables/upgrade")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "202", description = "Job ID for keyspace SSTable upgrade process",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(
                            implementation = String.class
                    ),
                    examples = @ExampleObject(
                            value = "d69d1d95-9348-4460-95d2-ae342870fade"
                    )
            )
    )
    @Operation(summary = "Rewrite sstables (for the requested tables) that are not on the current version (thus upgrading them to said current version). This operation is asynchronous and returns immediately.",
            operationId = "upgradeSSTables")
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

            return Response.accepted(ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService,
                    "CALL NodeOps.upgradeSSTables(?, ?, ?, ?, ?)", keyspaceName, !excludeCurrentVersion, keyspaceRequest.jobs, tables, true)).build();
        });
    }

    @POST
    @Path("/compact")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(responseCode = "202", description = "Job ID for keyspace compaction process",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(
                            implementation = String.class
                    ),
                    examples = @ExampleObject(
                            value = "d69d1d95-9348-4460-95d2-ae342870fade"
                    )
            )
    )
    @ApiResponse(responseCode = "400", description = "Invalid table compaction request",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN,
                    examples = @ExampleObject(
                            value = "Invalid option combination: Can not use split-output here"
                    )
            )
    )
    @Operation(summary = "Force a (major) compaction on one or more tables or user-defined compaction on given SSTables",
            operationId = "compact")
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
                    return Response.accepted(ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService,
                            "CALL NodeOps.forceUserDefinedCompaction(?, ?)", userDefinedFiles, true)).build();
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred during user defined compaction", e);
                }
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

            if (tokenProvided) {
                return Response.accepted(ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService,
                        "CALL NodeOps.forceKeyspaceCompactionForTokenRange(?, ?, ?, ?, ?)", keyspaceName,
                        compactRequest.startToken, compactRequest.endToken, tables, true)).build();
            }
            return Response.accepted(ResponseTools.getSingleRowStringResponse(app.dbUnixSocketFile, cqlService,
                    "CALL NodeOps.forceKeyspaceCompaction(?, ?, ?, ?)", compactRequest.splitOutput, keyspaceName,
                    compactRequest.tables, true)).build();
        });
    }
}
