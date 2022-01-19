/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import java.io.IOException;
import org.junit.After;

/**
 * This class adds and After method to stop the Management API container after each
 * individual test. Extend this if tests can't be run against the same running container
 * without predictable results.
 */
public abstract class BaseDockerIsolatedIntegrationTest extends BaseDockerIntegrationTest
{

  public BaseDockerIsolatedIntegrationTest(String version) throws IOException {
    super(version);
  }

    @After
    public void after()
    {
        docker.stopManagementAPI();
    }

}
