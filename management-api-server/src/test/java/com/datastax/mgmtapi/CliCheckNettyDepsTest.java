/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.junit.Assert.fail;

import java.security.Permission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link Cli#checkNettyDeps()}.
 *
 * <p>The method calls {@link System#exit(int)} on failure. We intercept that with a minimal {@link
 * SecurityManager} that converts the call into a testable {@link ExitException}.
 */
public class CliCheckNettyDepsTest {

  /** Thrown by {@link NoExitSecurityManager} instead of actually halting the JVM. */
  static final class ExitException extends SecurityException {
    final int status;

    ExitException(int status) {
      super("System.exit(" + status + ") intercepted");
      this.status = status;
    }
  }

  /**
   * Blocks {@code System.exit} by throwing {@link ExitException}. All other permission checks are
   * delegated to the original manager (or silently allowed when there is none).
   */
  static final class NoExitSecurityManager extends SecurityManager {
    private final SecurityManager delegate;

    NoExitSecurityManager(SecurityManager delegate) {
      this.delegate = delegate;
    }

    @Override
    public void checkExit(int status) {
      throw new ExitException(status);
    }

    @Override
    public void checkPermission(Permission perm) {
      if (delegate != null) {
        delegate.checkPermission(perm);
      }
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
      if (delegate != null) {
        delegate.checkPermission(perm, context);
      }
    }
  }

  private SecurityManager originalManager;

  @Before
  public void installNoExitManager() {
    originalManager = System.getSecurityManager();
    System.setSecurityManager(new NoExitSecurityManager(originalManager));
  }

  @After
  public void restoreSecurityManager() {
    System.setSecurityManager(originalManager);
  }

  @Test
  public void checkNettyDeps_doesNotExitWhenNativeTransportAvailable() {
    try {
      new Cli().checkNettyDeps();
    } catch (ExitException e) {
      fail("checkNettyDeps() called System.exit(" + e.status + ") unexpectedly");
    }
  }
}
