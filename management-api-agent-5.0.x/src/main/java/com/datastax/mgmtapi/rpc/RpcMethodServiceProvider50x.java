/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.lang.reflect.Method;

public class RpcMethodServiceProvider50x implements RpcMethodServiceProvider {

  @Override
  public RpcMethod getRpcMethod(Method method, RpcObject rpcObject) {
    return new RpcMethod50x(method, rpcObject);
  }
}
