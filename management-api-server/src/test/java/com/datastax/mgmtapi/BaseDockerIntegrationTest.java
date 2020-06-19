/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLException;

import com.google.common.collect.Lists;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.Parameterized;

import com.datastax.mgmtapi.helpers.DockerHelper;
import com.datastax.mgmtapi.helpers.NettyHttpClient;

public abstract class BaseDockerIntegrationTest
{
    protected static String BASE_PATH = "http://localhost:8080/api/v0";
    protected static URL BASE_URL;

    static
    {
        try
        {
            BASE_URL = new URL(BASE_PATH);
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException();
        }
    }

    public static class GrabSystemLogOnFailure extends TestWatcher
    {

        DockerHelper dockerHelper;

        @Override
        protected void failed(Throwable e, Description description)
        {
            if (null != dockerHelper)
            {
                int numberOfLines = 100;
                System.out.println(String.format("=====> Test %s failed - Showing last %s entries of system.log", description.getMethodName(), numberOfLines));
                dockerHelper.tailSystemLog(numberOfLines);
            }
        }
    }

    @Rule
    public GrabSystemLogOnFailure systemLogGrabber = new GrabSystemLogOnFailure();

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected final String version;
    protected static DockerHelper docker;

    @Parameterized.Parameters
    public static Iterable<String[]> functions()
    {
        List<String[]> l =  Lists.newArrayList(
                new String[]{"3_11"},
                new String[]{"4_0"}
        );

        if (Boolean.getBoolean("dseIncluded"))
            l.add(new String[]{"dse-68"});

        return l;
    }

    public BaseDockerIntegrationTest(String version)
    {
        this.version = version;
        docker.startManagementAPI(version, getEnvironmentVars());
    }

    @BeforeClass
    public static void setup() throws InterruptedException
    {
        try
        {
            temporaryFolder.create();
            docker = new DockerHelper(getTempDir());
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
    }

    @AfterClass
    public static void teardown()
    {
        try
        {
            docker.stopManagementAPI();
        }
        finally
        {
            //temporaryFolder.delete();
        }
    }

    @Before
    public void before()
    {
        systemLogGrabber.dockerHelper = docker;
    }

    protected ArrayList<String> getEnvironmentVars()
    {
        return Lists.newArrayList(
                "MGMT_API_NO_KEEP_ALIVE=true",
                "MGMT_API_EXPLICIT_START=true"
        );
    }

    protected static File getTempDir()
    {
        String os = System.getProperty("os.name");
        File tempDir = temporaryFolder.getRoot();
        if (os.equalsIgnoreCase("mac os x"))
        {
            tempDir = new File("/private", tempDir.getPath());
        }

        tempDir.setWritable(true, false);
        tempDir.setReadable(true, false);
        tempDir.setExecutable(true, false);

        return tempDir;
    }

    protected NettyHttpClient getClient() throws SSLException
    {
        return new NettyHttpClient(BASE_URL);
    }
}
