/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ObjectSerializer3xTest {

  @Test
  public void testFieldTypes() {
    ObjectSerializer3x<?> exampleSerializer = new ObjectSerializer3x<>(Example.class);

    expectType(exampleSerializer, "stringField", "org.apache.cassandra.db.marshal.UTF8Type");
    expectType(
        exampleSerializer,
        "mapField",
        "org.apache.cassandra.db.marshal.FrozenType("
            + "org.apache.cassandra.db.marshal.MapType("
            + "org.apache.cassandra.db.marshal.UTF8Type,"
            + "org.apache.cassandra.db.marshal.UTF8Type))");
    expectType(
        exampleSerializer,
        "listField",
        "org.apache.cassandra.db.marshal.FrozenType("
            + "org.apache.cassandra.db.marshal.ListType("
            + "org.apache.cassandra.db.marshal.ListType("
            + "org.apache.cassandra.db.marshal.Int32Type)))");
  }

  private void expectType(
      ObjectSerializer3x<?> objectSerializer, String fieldName, String expectedType) {
    assertThat(objectSerializer.serializers).containsKey(fieldName);
    assertThat(objectSerializer.serializers.get(fieldName).type).hasToString(expectedType);
  }

  @SuppressWarnings("unused")
  public static class Example {
    public String stringField;
    public Map<String, String> mapField;
    public List<List<Integer>> listField;
  }
}
