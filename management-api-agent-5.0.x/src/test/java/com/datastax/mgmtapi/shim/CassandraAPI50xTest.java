/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.shim;

import static org.mockito.Mockito.mockStatic;

import org.apache.cassandra.auth.CassandraAuthorizer;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.db.ConsistencyLevel;
import org.junit.Test;
import org.mockito.MockedStatic;

public class CassandraAPI50xTest {
  private static final String IDENTITY = "spiffe://example.test/user/1";
  private static final String ROLE = "schema_reader";

  @Test
  public void addsIdentityToRoleUsingAuthWriteConsistency() {
    try (MockedStatic<CassandraAuthorizer> authorizer = mockStatic(CassandraAuthorizer.class);
        MockedStatic<QueryProcessor> queryProcessor = mockStatic(QueryProcessor.class)) {
      authorizer
          .when(CassandraAuthorizer::authWriteConsistencyLevel)
          .thenReturn(ConsistencyLevel.LOCAL_QUORUM);

      new CassandraAPI50x().addIdentityToRole(IDENTITY, ROLE);

      queryProcessor.verify(
          () ->
              QueryProcessor.execute(
                  "INSERT INTO system_auth.identity_to_role (identity, role) VALUES (?, ?)",
                  ConsistencyLevel.LOCAL_QUORUM,
                  IDENTITY,
                  ROLE));
    }
  }

  @Test
  public void deletesIdentityToRoleUsingAuthWriteConsistency() {
    try (MockedStatic<CassandraAuthorizer> authorizer = mockStatic(CassandraAuthorizer.class);
        MockedStatic<QueryProcessor> queryProcessor = mockStatic(QueryProcessor.class)) {
      authorizer
          .when(CassandraAuthorizer::authWriteConsistencyLevel)
          .thenReturn(ConsistencyLevel.LOCAL_QUORUM);

      new CassandraAPI50x().deleteIdentityToRole(IDENTITY);

      queryProcessor.verify(
          () ->
              QueryProcessor.execute(
                  "DELETE FROM system_auth.identity_to_role WHERE identity = ?",
                  ConsistencyLevel.LOCAL_QUORUM,
                  IDENTITY));
    }
  }
}
