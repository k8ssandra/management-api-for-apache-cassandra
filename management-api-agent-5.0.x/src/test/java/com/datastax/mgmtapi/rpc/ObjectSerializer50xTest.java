/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSerializer50xTest
    extends ObjectSerializerTestBase<ObjectSerializer50x<Example>> {

  @Override
  protected ObjectSerializer50x<Example> createExampleSerializer() {
    return new ObjectSerializer50x<>(Example.class);
  }

  @Override
  protected String getCqlType(ObjectSerializer50x<Example> serializer, String fieldName) {
    assertThat(serializer.serializers).containsKey(fieldName);
    return serializer.serializers.get(fieldName).type.toString();
  }
}
