/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class UnixCmdsTest {

  @Test
  public void testIsPidRunning() throws Exception {
    // Print our own pid before exec'ing so the pid stays the same
    Process process = new ProcessBuilder("sh", "-c", "echo $$; exec sleep 60").start();
    try {
      int pid = readPid(process);
      assertTrue(UnixCmds.isPidRunning(pid));

      process.destroy();
      assertTrue(process.waitFor(10, TimeUnit.SECONDS));
      // The old implementation matched substrings of any running pid, so a dead pid which is
      // part of a running pid (such as a newly spawned "ps" process) could be reported as running
      for (int i = 0; i < 20; i++) {
        assertFalse(UnixCmds.isPidRunning(pid));
      }
    } finally {
      process.destroyForcibly();
    }
  }

  @Test
  public void testIsPidRunningZombie() throws Exception {
    // The background child exits immediately, but the parent (exec'd to sleep) never reaps it
    Process process = new ProcessBuilder("sh", "-c", "true & echo $!; exec sleep 60").start();
    try {
      int zombiePid = readPid(process);
      // Give the child time to exit
      Thread.sleep(500);
      assertFalse(UnixCmds.isPidRunning(zombiePid));
    } finally {
      process.destroyForcibly();
    }
  }

  @Test
  public void testIsPidRunningNonExistentPid() throws Exception {
    assertFalse(UnixCmds.isPidRunning(Integer.MAX_VALUE));
  }

  private static int readPid(Process process) throws Exception {
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    return Integer.parseInt(reader.readLine().trim());
  }
}
