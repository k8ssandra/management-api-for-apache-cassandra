/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSerializer41xTest
    extends ObjectSerializerTestBase<ObjectSerializer41x<Example>> {

  @Override
  protected ObjectSerializer41x<Example> createExampleSerializer() {
    return new ObjectSerializer41x<>(Example.class);
  }

  @Override
  protected String getCqlType(ObjectSerializer41x<Example> serializer, String fieldName) {
    assertThat(serializer.serializers).containsKey(fieldName);
    return serializer.serializers.get(fieldName).type.toString();
  }
}
