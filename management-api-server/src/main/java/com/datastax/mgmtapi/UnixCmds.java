/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.datastax.mgmtapi.util.ShellUtils;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ugly methods for doing Unix commands */
public class UnixCmds {
  private static final Logger logger = LoggerFactory.getLogger(UnixCmds.class);

  private static final String PS_CMD = "/bin/ps";
  private static final String KILL_CMD = "/bin/kill";
  private static final String WHICH_CMD = "/bin/which";

  public static Optional<File> whichCassandra() throws IOException {
    return which("cassandra");
  }

  public static Optional<File> whichDse() throws IOException {
    return which("dse");
  }

  private static Optional<File> which(String exeStr) throws IOException {
    return ShellUtils.executeWithHandlers(
        new ProcessBuilder(WHICH_CMD, exeStr),
        (input, err) ->
            input.findFirst().map(path -> new File(path.toLowerCase())).filter(File::canExecute),
        (exitCode, err) -> Optional.empty());
  }

  public static Optional<Integer> findDbProcessWithMatchingArg(String filterStr)
      throws IOException {

    ProcessBuilder psListPb = new ProcessBuilder(PS_CMD, "-eo", "pid,command");
    return ShellUtils.executeWithHandlers(
        psListPb,
        (input, err) -> {
          List<String> match =
              input.filter(x -> x.contains(filterStr)).collect(Collectors.toList());

          if (match.isEmpty()) {
            logger.debug("No process found for filtering criteria: {}", filterStr);
            return Optional.empty();
          }

          if (match.size() > 1) {
            throw new RuntimeException("Found more than 1 pid for: " + filterStr);
          }

          int pid = Integer.parseInt(match.get(0).trim().split("\\s")[0]);
          return Optional.of(pid);
        },
        (exitCode, err) -> Optional.empty());
  }

  public static boolean terminateProcess(int pid) throws IOException {
    return ShellUtils.executeWithHandlers(
        new ProcessBuilder(KILL_CMD, String.valueOf(pid)),
        (input, err) -> true,
        (exitCode, err) -> false);
  }

  public static boolean killProcess(int pid) throws IOException {
    return ShellUtils.executeWithHandlers(
        new ProcessBuilder(KILL_CMD, "-9", String.valueOf(pid)),
        (input, err) -> true,
        (exitCode, err) -> false);
  }
}
