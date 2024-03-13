/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.NoNodeAvailableException;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder;
import java.io.File;
import org.apache.http.ConnectionClosedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CqlService {
  private static final Logger logger = LoggerFactory.getLogger(CqlService.class);

  public ResultSet executeCql(File dbUnixSocketFile, String query)
      throws ConnectionClosedException {
    CqlSession session = UnixSocketCQLAccess.get(dbUnixSocketFile).orElse(null);

    if (session == null || session.isClosed()) {
      throw new ConnectionClosedException("Internal connection to Cassandra closed");
    }

    try {
      return session.execute(query);
    } catch (NoNodeAvailableException e) {
      try {
        session.close();
      } catch (Throwable t) {
        // close quietly
      }

      throw e;
    }
  }

  public ResultSet executePreparedStatement(
      File cassandraUnixSocketFile, String query, Object... values)
      throws ConnectionClosedException {
    CqlSession session = UnixSocketCQLAccess.get(cassandraUnixSocketFile).orElse(null);

    if (session == null || session.isClosed()) {
      throw new ConnectionClosedException("Internal connection to Cassandra closed");
    }

    SimpleStatementBuilder ssb = new SimpleStatementBuilder(query);

    for (Object obj : values) {
      ssb = ssb.addPositionalValue(obj);
    }

    try {
      return session.execute(ssb.build());
    } catch (NoNodeAvailableException e) {
      try {
        session.close();
      } catch (Throwable t) {
        // close quietly
      }

      throw e;
    }
  }

  /**
   * Used for NodeOpsProvider implementations that are synchronous and may take a while to complete.
   * (example node drain). The implementation here uses the Java driver's execution profile
   * mechanism with the bundled application.conf that sets the request timeout to 0 seconds, which
   * effectively disables the driver request timeout. Use this method with caution!. See
   * application.conf in the resources folder.
   */
  public ResultSet executeSlowCql(File dbUnixSocketFile, String query)
      throws ConnectionClosedException {
    CqlSession session = UnixSocketCQLAccess.get(dbUnixSocketFile).orElse(null);

    if (session == null || session.isClosed()) {
      throw new ConnectionClosedException("Internal connection to Cassandra closed");
    }

    // build a statement and use the "slow" driver execution profile
    SimpleStatementBuilder ssb = new SimpleStatementBuilder(query).setExecutionProfileName("slow");

    try {
      return session.execute(ssb.build());
    } catch (NoNodeAvailableException e) {
      try {
        session.close();
      } catch (Throwable t) {
        // close quietly
      }

      throw e;
    }
  }
}
