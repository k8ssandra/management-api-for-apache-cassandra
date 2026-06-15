/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSerializer60xTest
    extends ObjectSerializerTestBase<ObjectSerializer60x<Example>> {

  @Override
  protected ObjectSerializer60x<Example> createExampleSerializer() {
    return new ObjectSerializer60x<>(Example.class);
  }

  @Override
  protected String getCqlType(ObjectSerializer60x<Example> serializer, String fieldName) {
    assertThat(serializer.serializers).containsKey(fieldName);
    return serializer.serializers.get(fieldName).type.toString();
  }
}
