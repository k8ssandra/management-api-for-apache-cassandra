/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSerializer51xTest
    extends ObjectSerializerTestBase<ObjectSerializer51x<Example>> {

  @Override
  protected ObjectSerializer51x<Example> createExampleSerializer() {
    return new ObjectSerializer51x<>(Example.class);
  }

  @Override
  protected String getCqlType(ObjectSerializer51x<Example> serializer, String fieldName) {
    assertThat(serializer.serializers).containsKey(fieldName);
    return serializer.serializers.get(fieldName).type.toString();
  }
}
