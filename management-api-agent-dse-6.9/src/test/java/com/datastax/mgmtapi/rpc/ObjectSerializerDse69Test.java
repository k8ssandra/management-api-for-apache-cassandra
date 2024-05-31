/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSerializerDse69Test
    extends ObjectSerializerTestBase<ObjectSerializerDse69<Example>> {

  @Override
  protected ObjectSerializerDse69<Example> createExampleSerializer() {
    return new ObjectSerializerDse69<>(Example.class);
  }

  @Override
  protected String getCqlType(ObjectSerializerDse69<Example> serializer, String fieldName) {
    assertThat(serializer.serializers).containsKey(fieldName);
    return serializer.serializers.get(fieldName).type.toString();
  }
}
