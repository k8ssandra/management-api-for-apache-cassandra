/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.CqlService;
import com.datastax.mgmtapi.ManagementApplication;
import com.datastax.mgmtapi.resources.models.CreateKeyspaceRequest;
import com.datastax.mgmtapi.resources.models.KeyspaceRequest;
import io.swagger.v3.oas.annotations.Operation;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpStatus;

@Path("/api/v0/ops/keyspace")
public class KeyspaceOpsResources
{
    private static final Logger logger = LoggerFactory.getLogger(KeyspaceOpsResources.class);

    private final ManagementApplication app;
    private final CqlService cqlService;

    public KeyspaceOpsResources(ManagementApplication application) {
        this.app = application;
        this.cqlService = application.cqlService;
    }

    @POST
    @Path("/cleanup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Triggers the immediate cleanup of keys no longer belonging to a node. By default, clean all keyspaces")
    public Response cleanup(KeyspaceRequest keyspaceRequest)
    {
        return NodeOpsResources.handle(() ->
        {
            List<String> tables = keyspaceRequest.tables;
            if (CollectionUtils.isEmpty(tables)) {
                tables = new ArrayList<>();
            }

            String keyspaceName = keyspaceRequest.keyspaceName;
            if (StringUtils.isBlank(keyspaceName))
            {
                keyspaceName = "NON_LOCAL_STRATEGY";
            }

            if (StringUtils.equalsAnyIgnoreCase(keyspaceName, "system", "system_schema"))
            {
                return Response.ok("OK").build();
            }

            cqlService.executePreparedStatement(app.dbUnixSocketFile,
                    "CALL NodeOps.forceKeyspaceCleanup(?, ?, ?)", keyspaceRequest.jobs, keyspaceName, tables);

            return Response.ok("OK").build();
        });
    }

    @POST
    @Path("/refresh")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Load newly placed SSTables to the system without restart")
    public Response refresh(@QueryParam(value="keyspaceName")String keyspaceName, @QueryParam(value="table")String table)
    {
        try
        {
            if (StringUtils.isBlank(keyspaceName))
            {
                return Response.status(HttpStatus.SC_BAD_REQUEST).entity("Must provide a keyspace name").build();
            }

            if (StringUtils.isBlank(table))
            {
                return Response.status(HttpStatus.SC_BAD_REQUEST).entity("table must be provided").build();
            }

            cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.loadNewSSTables(?, ?)", keyspaceName, table);

            return Response.ok("OK").build();
        }
        catch (ConnectionClosedException e)
        {
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity("Internal connection to Cassandra closed").build();
        }
        catch (Throwable t)
        {
            logger.error("Error when executing request", t);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity(t.getLocalizedMessage()).build();
        }
    }

    @POST
    @Path("/create")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a new keyspace with the given name and replication settings")
    public Response create(CreateKeyspaceRequest createKeyspaceRequest)
    {
        try
        {
            if (StringUtils.isBlank(createKeyspaceRequest.keyspaceName))
            {
                return Response.status(HttpStatus.SC_BAD_REQUEST).entity("Keyspace creation failed. Non-empty 'keyspace_name' must be provided").build();
            }

            if (null == createKeyspaceRequest.replicationSettings || createKeyspaceRequest.replicationSettings.isEmpty())
            {
                return Response.status(HttpStatus.SC_BAD_REQUEST).entity("Keyspace creation failed. 'replication_settings' must be provided").build();
            }

            cqlService.executePreparedStatement(app.dbUnixSocketFile, "CALL NodeOps.createKeyspace(?, ?)",
                    createKeyspaceRequest.keyspaceName, createKeyspaceRequest.replicationSettingsAsMap());

            return Response.ok("OK").build();
        }
        catch (ConnectionClosedException e)
        {
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity("Internal connection to Cassandra closed").build();
        }
        catch (Throwable t)
        {
            logger.error("Error when executing request", t);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity(t.getLocalizedMessage()).build();
        }
    }
}
