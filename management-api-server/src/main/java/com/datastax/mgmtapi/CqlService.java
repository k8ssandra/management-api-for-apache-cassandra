/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.NoNodeAvailableException;
import org.apache.http.ConnectionClosedException;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder;

public class CqlService
{
    private static final Logger logger = LoggerFactory.getLogger(CqlService.class);

    public ResultSet executeCql(File dbUnixSocketFile, String query) throws ConnectionClosedException
    {
        CqlSession session = UnixSocketCQLAccess.get(dbUnixSocketFile).orElse(null);

        if (session == null || session.isClosed())
        {
            throw new ConnectionClosedException("Internal connection to Cassandra closed");
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

    public ResultSet executePreparedStatement(File cassandraUnixSocketFile, String query,  Object... values) throws ConnectionClosedException
    {
        CqlSession session = UnixSocketCQLAccess.get(cassandraUnixSocketFile).orElse(null);

        if (session == null || session.isClosed())
        {
            throw new ConnectionClosedException("Internal connection to Cassandra closed");
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
