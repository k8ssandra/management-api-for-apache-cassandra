/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.interceptors;

import static com.datastax.mgmtapi.interceptors.SystemDistributedReplicationInterceptor.SYSTEM_DISTRIBUTED_NTS_DC_RF_OVERRIDE_PROPERTY;
import static org.junit.Assert.assertEquals;

import java.util.Map;
import org.junit.Test;

public class SystemDistributedReplicationInterceptorTests {

  @Test
  public void testSystemDistributedReplicationParsing() {
    System.setProperty(SYSTEM_DISTRIBUTED_NTS_DC_RF_OVERRIDE_PROPERTY, "dc1:4");
    Map<String, String> rfOverrides = SystemDistributedReplicationInterceptor.parseDcRfOverrides();
    System.clearProperty(SYSTEM_DISTRIBUTED_NTS_DC_RF_OVERRIDE_PROPERTY);
    assertEquals(1, rfOverrides.size());
    assertEquals("4", rfOverrides.get("dc1"));

    // Unicode version
    System.setProperty(SYSTEM_DISTRIBUTED_NTS_DC_RF_OVERRIDE_PROPERTY, "dc\u00201:1,dc1:2");
    rfOverrides = SystemDistributedReplicationInterceptor.parseDcRfOverrides();
    System.clearProperty(SYSTEM_DISTRIBUTED_NTS_DC_RF_OVERRIDE_PROPERTY);
    assertEquals(2, rfOverrides.size());
    assertEquals("1", rfOverrides.get("dc 1"));
    assertEquals("2", rfOverrides.get("dc1"));
  }
}
