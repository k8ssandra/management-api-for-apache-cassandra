/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSerializerHcdTest
    extends ObjectSerializerTestBase<ObjectSerializerHcd<Example>> {

  @Override
  protected ObjectSerializerHcd<Example> createExampleSerializer() {
    return new ObjectSerializerHcd<>(Example.class);
  }

  @Override
  protected String getCqlType(ObjectSerializerHcd<Example> serializer, String fieldName) {
    assertThat(serializer.serializers).containsKey(fieldName);
    return serializer.serializers.get(fieldName).type.toString();
  }
}
