/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import com.google.common.base.Throwables;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessControlException;
import org.apache.cassandra.exceptions.ExceptionCode;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RpcExecutionException extends RequestExecutionException {
  private static final Logger logger = LoggerFactory.getLogger(RpcExecutionException.class);

  public RpcExecutionException(ExceptionCode exceptionCode, String msg, Throwable cause) {
    super(exceptionCode, msg, cause);
  }

  public static RpcExecutionException create(String msg, Throwable cause) {
    if (cause instanceof InvocationTargetException) {
      cause = ((InvocationTargetException) cause).getTargetException();
    }

    Throwable root = Throwables.getRootCause(cause);

    if (root instanceof AccessControlException) {
      logger.info(msg);
      return new RpcExecutionException(ExceptionCode.UNAUTHORIZED, root.getMessage(), cause);
    } else {
      logger.info(msg, cause);
      return new RpcExecutionException(ExceptionCode.SERVER_ERROR, msg, cause);
    }
  }
}
