/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSerializerDse68Test
    extends ObjectSerializerTestBase<ObjectSerializerDse68<Example>> {

  @Override
  protected ObjectSerializerDse68<Example> createExampleSerializer() {
    return new ObjectSerializerDse68<>(Example.class);
  }

  @Override
  protected String getCqlType(ObjectSerializerDse68<Example> serializer, String fieldName) {
    assertThat(serializer.serializers).containsKey(fieldName);
    return serializer.serializers.get(fieldName).type.toString();
  }
}
