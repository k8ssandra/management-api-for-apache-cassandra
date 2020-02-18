/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.bdp.management;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.dse.driver.api.core.DseSession;
import com.datastax.oss.driver.api.core.NoNodeAvailableException;
import com.datastax.oss.driver.api.core.metadata.Node;
import org.apache.http.ConnectionClosedException;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder;

public class CqlService
{
    private static final Logger logger = LoggerFactory.getLogger(CqlService.class);

    public ResultSet executeCql(File dseUnixSocketFile, String query) throws ConnectionClosedException
    {
        CqlSession session = UnixSocketCQLAccess.get(dseUnixSocketFile).orElse(null);

        if (session == null || session.isClosed())
        {
            throw new ConnectionClosedException("Internal connection to DSE closed");
        }

        try
        {
            return session.execute(query);
        }
        catch (NoNodeAvailableException e)
        {
            try
            {
                session.close();
            }
            catch (Throwable t)
            {
                // close quietly
            }

            throw e;
        }
    }

    public ResultSet executePreparedStatement(File dseUnixSocketFile, String query,  Object... values) throws ConnectionClosedException
    {
        CqlSession session = UnixSocketCQLAccess.get(dseUnixSocketFile).orElse(null);

        if (session == null || session.isClosed())
        {
            throw new ConnectionClosedException("Internal connection to DSE closed");
        }

        SimpleStatementBuilder ssb = new SimpleStatementBuilder(query);

        for (Object obj : values)
        {
            ssb = ssb.addPositionalValue(obj);
        }

        try
        {
            return session.execute(ssb.build());
        }
        catch (NoNodeAvailableException e)
        {
            try
            {
                session.close();
            }
            catch (Throwable t)
            {
                // close quietly
            }

            throw e;
        }
    }
}
