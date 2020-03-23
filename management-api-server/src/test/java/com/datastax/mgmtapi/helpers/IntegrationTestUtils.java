/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.helpers;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;

public class IntegrationTestUtils
{
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTestUtils.class);

    public static boolean shouldRun()
    {
        boolean epollAvailable = false;
        boolean kqueueAvailable = false;

        try
        {
            epollAvailable = Epoll.isAvailable();
        } catch (Exception e)
        {
            logger.debug("Epoll is not available", e);
        }

        try
        {
            kqueueAvailable = KQueue.isAvailable();
        } catch (Exception e)
        {
            logger.debug("KQueue is not available", e);
        }

        return epollAvailable || kqueueAvailable;
    }

    public static List<String> getExtraArgs(Class testClass, String suffix, File tempDir)
    {
        return getExtraArgs(testClass, suffix, tempDir, ThreadLocalRandom.current().nextInt(1024));
    }

    public static List<String> getExtraArgs(Class testClass, String suffix, File tempDir, int offset)
    {
        logger.info("Test OFFSET is {}", offset);
        File logDir = new File("/tmp", testClass.getName() + "." + suffix + "." + offset);
        logDir.mkdirs();

        return ImmutableList.of(
                "-Dcassandra.jmx.remote.port=" + (7199 + offset),
                "-Dcassandra.native_transport_port=" + (9042 + offset),
                "-Dcassandra.storagedir=" + tempDir.getAbsolutePath(),
                "-Dcassandra.logdir=" + logDir.getAbsolutePath(),
                "-Dcassandra.superuser_setup_delay_ms=100",
                "-Dstart_native_transport=false");
    }

    public static String getCassandraHome()
    {
        return System.getProperty("CASSANDRA_HOME");
    }

    public static File getFile(Class resourceClass, String fileName){
        try
        {
            return new File(URLDecoder.decode(resourceClass.getResource(fileName).getFile(), "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            return new File(resourceClass.getResource(fileName).getFile());
        }
    }
}
