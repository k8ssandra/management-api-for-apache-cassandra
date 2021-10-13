package com.datastax.mgmtapi.resources.helpers;

import com.datastax.mgmtapi.CqlService;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.apache.http.ConnectionClosedException;

import java.io.File;

public class ResponseTools {

    public static String getSingleRowStringResponse(final File dbUnixSocketFile, CqlService cqlService, String query, Object... params) throws ConnectionClosedException {
        Row row = getRow(dbUnixSocketFile, cqlService, query, params);
        String queryResponse = null;
        if (row != null)
        {
            queryResponse = row.getString(0);
        }

        return queryResponse;
    }

    public static Object getSingleRowResponse(final File dbUnixSocketFile, CqlService cqlService, String query, Object... params) throws ConnectionClosedException {
        Row row = getRow(dbUnixSocketFile, cqlService, query, params);
        Object queryResponse = null;
        if (row != null)
        {
            queryResponse = row.getObject(0);
        }

        return queryResponse;
    }

    private static Row getRow(File dbUnixSocketFile, CqlService cqlService, String query, Object[] params) throws ConnectionClosedException {
        ResultSet rs;

        if(params.length > 0) {
            rs = cqlService.executePreparedStatement(dbUnixSocketFile, query, params);
        } else {
            rs = cqlService.executeCql(dbUnixSocketFile, query);
        }

        Row row = rs.one();
        return row;
    }

}
