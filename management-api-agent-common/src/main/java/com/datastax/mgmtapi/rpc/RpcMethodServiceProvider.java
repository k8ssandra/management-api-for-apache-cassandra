/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.lang.reflect.Method;

public interface RpcMethodServiceProvider {

  RpcMethod getRpcMethod(Method method, RpcObject rpcObject);
}
