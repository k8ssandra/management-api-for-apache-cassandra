/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// From fallout
public class ShellUtils {
  private static final Logger logger = LoggerFactory.getLogger(ShellUtils.class);

  private static final long PS_WAIT_FOR_TIMEOUT_S = 600;
  private static final long PS_MAX_LINES_COLLECTED = 10000;

  public static Process executeShell(
      ProcessBuilder processBuilder, Map<String, String> environment) {
    if (logger.isTraceEnabled()) {
      String cmd = String.join(" ", processBuilder.command());
      logger.trace("Executing locally: {}, Env {}", cmd, environment);
    }
    processBuilder.environment().putAll(environment);
    try {
      return processBuilder.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public interface ThrowingBiFunction<ArgType, Arg2Type, ResType> {
    ResType apply(ArgType t, Arg2Type t2) throws IOException;
  }

  public static <T> T executeWithHandlers(
      ProcessBuilder processBuilder,
      ThrowingBiFunction<Stream<String>, Stream<String>, T> handler,
      ThrowingBiFunction<Integer, Stream<String>, T> errorHandler)
      throws IOException {
    return executeWithHandlers(processBuilder, handler, errorHandler, Collections.emptyMap());
  }

  public static <T> T executeWithHandlers(
      ProcessBuilder processBuilder,
      ThrowingBiFunction<Stream<String>, Stream<String>, T> handler,
      ThrowingBiFunction<Integer, Stream<String>, T> errorHandler,
      Map<String, String> environment)
      throws IOException {
    Process ps = ShellUtils.executeShell(processBuilder, environment);

    return runProcessWithHandlers(ps, handler, errorHandler);
  }

  private static <T> T runProcessWithHandlers(
      Process process,
      ThrowingBiFunction<Stream<String>, Stream<String>, T> handler,
      ThrowingBiFunction<Integer, Stream<String>, T> errorHandler)
      throws IOException {
    try (BufferedReader input =
            new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader error =
            new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
      // we need to read all the output and error for the ps to finish
      List<String> inputLines =
          input.lines().limit(PS_MAX_LINES_COLLECTED).collect(Collectors.toList());
      List<String> errorLines =
          error.lines().limit(PS_MAX_LINES_COLLECTED).collect(Collectors.toList());
      process.waitFor(PS_WAIT_FOR_TIMEOUT_S, TimeUnit.SECONDS);
      if (process.exitValue() != 0) {
        return errorHandler.apply(process.exitValue(), errorLines.stream());
      } else {
        return handler.apply(inputLines.stream(), errorLines.stream());
      }
    } catch (InterruptedException t) {
      throw new RuntimeException(t);
    }
  }
}
