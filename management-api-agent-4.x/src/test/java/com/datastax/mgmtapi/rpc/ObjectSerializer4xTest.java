/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSerializer4xTest extends ObjectSerializerTestBase<ObjectSerializer4x<Example>> {

  @Override
  protected ObjectSerializer4x<Example> createExampleSerializer() {
    return new ObjectSerializer4x<>(Example.class);
  }

  @Override
  protected String getCqlType(ObjectSerializer4x<Example> serializer, String fieldName) {
    assertThat(serializer.serializers).containsKey(fieldName);
    return serializer.serializers.get(fieldName).type.toString();
  }
}
