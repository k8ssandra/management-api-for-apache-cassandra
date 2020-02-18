/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.lang.reflect.InvocationTargetException;
import java.security.AccessControlException;
import java.util.Optional;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.exceptions.ExceptionCode;
import org.apache.cassandra.exceptions.RequestExecutionException;

final class RpcExecutionException extends RequestExecutionException
{
    private final static Logger logger = LoggerFactory.getLogger(RpcExecutionException.class);
    
    public RpcExecutionException(ExceptionCode exceptionCode, String msg, Throwable cause)
    {
        super(exceptionCode, msg, cause);
    }

    public static RpcExecutionException create(String msg, Throwable cause) {
        if (cause instanceof InvocationTargetException)
        {
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
