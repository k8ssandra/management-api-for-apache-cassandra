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
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.Parameterized;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.mgmtapi.helpers.DockerHelper;
import com.datastax.mgmtapi.helpers.NettyHttpClient;

public abstract class BaseDockerIntegrationTest
{
    protected static final Logger logger = LoggerFactory.getLogger(BaseDockerIntegrationTest.class);
    protected static final String BASE_PATH = "http://localhost:8080/api/v0";
    protected static final URL BASE_URL;

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

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher()
    {
        protected void starting(Description description)
        {
            System.out.println();
            System.out.println("--------------------------------------");
            System.out.printf("Starting %s...%n", description.getDisplayName());
        }

        @Override
        protected void failed(Throwable e, Description description)
        {
            System.out.flush();
            System.err.printf("FAILURE: %s%n", description);
            e.printStackTrace();
            System.err.flush();

            if (null != docker)
            {
                int numberOfLines = 100;
                System.out.printf("=====> Showing last %d entries of system.log%n", numberOfLines);
                docker.tailSystemLog(numberOfLines);
                System.out.printf("=====> End of last %d entries of system.log%n", numberOfLines);
                System.out.flush();
            }

        }

        protected void succeeded(Description description)
        {
            System.out.printf("SUCCESS: %s%n", description.getDisplayName());
        }

        protected void skipped(AssumptionViolatedException e, Description description)
        {
            System.out.printf("SKIPPED: %s%n", description.getDisplayName());
        }

        protected void finished(Description description)
        {
            System.out.println("--------------------------------------");
            System.out.println();
        }
    };

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected final String version;
    protected static DockerHelper docker;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static List<String> testVersions()
    {
        List<String> versions = new ArrayList<>(3);

        if (Boolean.getBoolean("run311tests"))
            versions.add("3_11");
        if (Boolean.getBoolean("run40tests"))
            versions.add("4_0");
        if (Boolean.getBoolean("runDSEtests"))
            versions.add("dse-68");

        return versions;
    }

    public BaseDockerIntegrationTest(String version) throws IOException
    {
        this.version = version;

        //If run without forking we need to start a new version
        if (docker != null)
        {
            temporaryFolder.delete();
            temporaryFolder.create();
            docker.startManagementAPI(version, getEnvironmentVars());
        }
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
        if (!docker.started())
        {
            docker.startManagementAPI(version, getEnvironmentVars());
        }
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
