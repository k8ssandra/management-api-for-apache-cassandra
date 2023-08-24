/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSerializer3xTest extends ObjectSerializerTestBase<ObjectSerializer3x<Example>> {

  @Override
  protected ObjectSerializer3x<Example> createExampleSerializer() {
    return new ObjectSerializer3x<>(Example.class);
  }

  @Override
  protected String getCqlType(ObjectSerializer3x<Example> serializer, String fieldName) {
    assertThat(serializer.serializers).containsKey(fieldName);
    return serializer.serializers.get(fieldName).type.toString();
  }
}
