/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.util.ShellUtils;

/**
 * Ugly methods for doing Unix commands
 */
public class UnixCmds
{
    private static final Logger logger = LoggerFactory.getLogger(UnixCmds.class);

    public static Optional<File> which(String exeStr) throws IOException
    {
        return ShellUtils.executeShellWithHandlers(
                String.format("/bin/which %s", exeStr),
                (input, err) -> {
                    File exe = new File(input.readLine().toLowerCase());
                    if (exe.canExecute())
                        return Optional.of(exe);

                    return Optional.empty();
                },
                (exitCode, err) -> Optional.empty()
        );
    }

    public static Optional<Integer> findDbProcessWithMatchingArg(String filterStr) throws IOException
    {
        return ShellUtils.executeShellWithHandlers(
                "/bin/ps -eo pid,command= | grep Dcassandra.server_process",
                (input, err) -> {

                    Integer pid = null;
                    String line;
                    while ( (line = input.readLine()) != null)
                    {
                        if (line.contains(filterStr))
                        {
                            if (pid != null)
                                throw new RuntimeException("Found more than 1 pid for: " + filterStr);

                            logger.debug("Match found on {}", line);
                            pid = Integer.valueOf(line.trim().split("\\s")[0]);
                        }
                    }

                    return Optional.ofNullable(pid);
                },
                (exitCode, err) -> Optional.empty()
        );
    }

}
