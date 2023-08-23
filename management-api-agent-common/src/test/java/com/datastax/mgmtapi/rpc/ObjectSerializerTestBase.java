/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/** A template for ObjectSerializer tests in implementation modules. */
public abstract class ObjectSerializerTestBase<S extends ObjectSerializer<Example>> {

  /** Create a concrete serializer for the {@link Example} class. */
  protected abstract S createExampleSerializer();

  /** Return the CQL type that the serializer inferred for a particular field. */
  protected abstract String getCqlType(S serializer, String fieldName);

  @Test
  public void testFieldTypes() {
    S exampleSerializer = createExampleSerializer();

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

  private void expectType(S exampleSerializer, String fieldName, String expectedType) {
    String actualType = getCqlType(exampleSerializer, fieldName);
    assertThat(actualType).isEqualTo(expectedType);
  }
}
