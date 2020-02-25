/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

//From fallout
public class ShellUtils
{
    private static final Logger logger = LoggerFactory.getLogger(ShellUtils.class);


    // This is from http://stackoverflow.com/a/20725050/322152.  We're not using org.apache.commons
    // .exec.CommandLine
    // because it fails to parse "run 'echo "foo"'" correctly (v1.3 misses off the final ')
    public static String[] split(CharSequence string)
    {
        List<String> tokens = new ArrayList<>();
        boolean escaping = false;
        char quoteChar = ' ';
        boolean quoting = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < string.length(); i++)
        {
            char c = string.charAt(i);
            if (escaping)
            {
                current.append(c);
                escaping = false;
            }
            else if (c == '\\' && !(quoting && quoteChar == '\''))
            {
                escaping = true;
            }
            else if (quoting && c == quoteChar)
            {
                quoting = false;
            }
            else if (!quoting && (c == '\'' || c == '"'))
            {
                quoting = true;
                quoteChar = c;
            }
            else if (!quoting && Character.isWhitespace(c))
            {
                if (current.length() > 0)
                {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
            }
            else
            {
                current.append(c);
            }
        }
        if (current.length() > 0)
        {
            tokens.add(current.toString());
        }
        return tokens.toArray(new String[]{});
    }

    public static String escape(String param)
    {
        return escape(
                param,
                false
        );
    }

    public static String escape(
            String param,
            boolean forceQuote
    )
    {
        String escapedQuotesParam =  param.replaceAll("'", "'\"'\"'");

        return forceQuote || escapedQuotesParam.contains(" ") ?
               "'" + escapedQuotesParam + "'" :
               escapedQuotesParam;
    }

    public static List<String> wrapCommandWithBash(
            String command,
            boolean remoteCommand
    )
    {
        List<String> fullCmd = new ArrayList<>();
        fullCmd.add("/bin/bash");
        fullCmd.add("-o");
        fullCmd.add("pipefail"); // pipe returns first non-zero exit code
        if (remoteCommand)
        {
            // Remote commands should be run in a login shell, since they'll need the environment
            // to be set up correctly.  Local commands should already be in this situation,
            // since fallout should have been run with the correct environment already in place.
            fullCmd.add("-l");
        }
        fullCmd.add("-c"); // execute following command
        if (remoteCommand)
        {
            // Remote commands need to be quoted again, to prevent expansion as they're passed to ssh.
            String escapedCmd = ShellUtils.escape(
                    command,
                    true
            );
            fullCmd.add(escapedCmd);
        }
        else
        {
            fullCmd.add(command);
        }
        return fullCmd;
    }

    public static Process executeShell(
            String command,
            Map<String, String> environment
    )
    {
        List<String> cmds = wrapCommandWithBash(
                command,
                false
        );
        logger.trace(
                "Executing locally: {}, Env {}",
                String.join(
                        " ",
                        cmds
                ),
                environment
        );
        ProcessBuilder pb = new ProcessBuilder(cmds);
        pb.environment().putAll(environment);
        try
        {
            return pb.start();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public interface ThrowingBiFunction<ArgType, Arg2Type, ResType>
    {
        ResType apply(
                ArgType t,
                Arg2Type t2
        ) throws IOException;
    }


    public static <T> T executeShellWithHandlers(
            String command,
            ThrowingBiFunction<BufferedReader, BufferedReader, T> handler,
            ThrowingBiFunction<Integer, BufferedReader, T> errorHandler
    )
            throws IOException
    {
        return executeShellWithHandlers(
                command,
                handler,
                errorHandler,
                Collections.emptyMap()
        );
    }

    public static <T> T executeShellWithHandlers(
            String command,
            ThrowingBiFunction<BufferedReader, BufferedReader, T> handler,
            ThrowingBiFunction<Integer, BufferedReader, T> errorHandler,
            Map<String, String> environment
    )
            throws IOException
    {
        Process ps = ShellUtils.executeShell(
                command,
                environment
        );

        // We need to read _everything_ from stdin + stderr as buffering will interfere with large amounts of
        // data. If we don't read everything here, the process might just get blocked, because of a buffer
        // being full.
        ByteArrayOutputStream stdinBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        int ec;
        try (InputStream stdin = ps.getInputStream(); InputStream stderr = ps.getErrorStream())
        {
            byte[] buf = new byte[1024];
            while (true)
            {
                int avStdin;
                int avStderr;
                int rd;

                avStdin = Math.min(stdin.available(), buf.length);
                if (avStdin > 0)
                {
                    rd = stdin.read(buf, 0, avStdin);
                    if (rd > 0)
                        stdinBuffer.write(buf, 0, rd);
                }
                avStderr = Math.min(stderr.available(), buf.length);
                if (avStderr > 0)
                {
                    rd = stderr.read(buf, 0, avStderr);
                    if (rd > 0)
                        stderrBuffer.write(buf, 0, rd);
                }

                if (avStdin == 0 && avStderr == 0)
                {
                    try
                    {
                        ec = ps.exitValue();
                        break;
                    }
                    catch (IllegalThreadStateException ignore)
                    {
                        try
                        {
                            Thread.sleep(50L);
                        }
                        catch (InterruptedException e)
                        {
                            //ignore
                        }
                    }
                }
            }
        }

        try (BufferedReader input = new BufferedReader(new StringReader(stdinBuffer.toString("UTF-8")));
             BufferedReader error = new BufferedReader(new StringReader(stderrBuffer.toString("UTF-8"))))
        {
            if (ec != 0)
            {
                return errorHandler.apply(
                        ps.exitValue(),
                        error
                );
            }

            return handler.apply(
                    input,
                    error
            );
        }
    }
}

