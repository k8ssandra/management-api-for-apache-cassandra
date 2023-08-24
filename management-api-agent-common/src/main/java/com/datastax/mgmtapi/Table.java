/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.util.Map;

public class Table {
  public final String name;
  public final Map<String, String> compaction;

  public Table(String name, Map<String, String> compaction) {
    this.name = name;
    this.compaction = compaction;
  }
}
