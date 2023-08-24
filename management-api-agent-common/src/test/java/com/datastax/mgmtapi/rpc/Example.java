/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.util.List;
import java.util.Map;

/** Example class used for serializer tests. */
@SuppressWarnings("unused")
public class Example {
  public String stringField;
  public Map<String, String> mapField;
  public List<List<Integer>> listField;
}
