/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *  Method annotation for methods that can be called via RPC.
 *
 *  ANNOTATING WITH RPC INDICATES THAT THE METHOD IS THREAD SAFE.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Rpc
{
    String name();
    boolean multiRow() default false;
}
