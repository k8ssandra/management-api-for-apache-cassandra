/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *  Parameter annotation for method parameters that can be called via RPC.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcParam
{
    String name();
    String help() default "";
}
