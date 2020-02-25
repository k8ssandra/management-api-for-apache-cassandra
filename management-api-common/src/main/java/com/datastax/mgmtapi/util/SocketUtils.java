/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

package com.datastax.mgmtapi.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocketUtils
{
    // http://man7.org/linux/man-pages/man7/unix.7.html
    // note: osx has a limit of 104 so using that as the lower bound
    // -1 to account for the control char
    public static final Integer MAX_SOCKET_NAME = 103;

    private final static Logger logger = LoggerFactory.getLogger(SocketUtils.class);

    public final static SocketUtils instance = new SocketUtils();

    private SocketUtils() { }

    /**
     * Helper method to create a valid Unix socket file path limited to {@link #MAX_SOCKET_NAME}({@value #MAX_SOCKET_NAME}) characters.
     *
     * @param socketDir optional, directory to use instead of {@code java.io.tmpdir}. Can be {@code null}.
     * @param name mandatory, name of the socket file
     * @return valid socket file path (absolute)
     */
    public static String makeValidUnixSocketFile(String socketDir, String name)
    {
        File f;

        if (socketDir == null)
            socketDir = System.getProperty("java.io.tmpdir");

        File fSocketDir = new File(socketDir);
        if (!fSocketDir.isDirectory() && !fSocketDir.mkdirs())
            throw new RuntimeException(String.format("%s is not a directory and could not be created", fSocketDir));

        try
        {

            // Tests create the socket files in a per-test-run directory. Withtout this hack, the socket files
            // would land in the per-Node instance directory (ng/SOME-UUID), which easily exceeds the 103/107
            // character limit, so we put the socket files into the shorter built/test/tmp/xyz directories.
            f = File.createTempFile(name + "-", ".sock", fSocketDir);
            try
            {
                Files.delete(f.toPath());
            }
            catch (IOException e)
            {
                //ignore
            }

            if (f.getAbsolutePath().length() > SocketUtils.MAX_SOCKET_NAME)
            {
                logger.warn("System property cassandra.collectd.socketdir overrides the default temp directory to '{}'," +
                        "but its path name is too long. Falling back to /tmp", socketDir);
                socketDir = "/tmp";
                f = File.createTempFile(name + "-", ".sock", new File("/tmp"));
                try
                {
                    Files.delete(f.toPath());
                }
                catch (IOException e)
                {
                    //ignore
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to create socket file in " + socketDir, e);
        }

        String socketFile = f.getAbsolutePath();

        if (socketFile.length() > SocketUtils.MAX_SOCKET_NAME)
        {
            String tmp = socketFile.substring(0, SocketUtils.MAX_SOCKET_NAME);
            logger.warn("The unix socket ({}) path is greater than the unix standard limit, cropping to {}", socketFile, tmp);
            socketFile = tmp;
        }

        return socketFile;
    }

}
