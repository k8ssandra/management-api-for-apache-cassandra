/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSerializerDse7Test
    extends ObjectSerializerTestBase<ObjectSerializerDse7<Example>> {

  @Override
  protected ObjectSerializerDse7<Example> createExampleSerializer() {
    return new ObjectSerializerDse7<>(Example.class);
  }

  @Override
  protected String getCqlType(ObjectSerializerDse7<Example> serializer, String fieldName) {
    assertThat(serializer.serializers).containsKey(fieldName);
    return serializer.serializers.get(fieldName).type.toString();
  }
}
