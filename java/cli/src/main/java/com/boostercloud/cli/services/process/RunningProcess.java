package com.boostercloud.cli.services.process;

/** A process started with {@link ProcessService#spawn}. */
public interface RunningProcess {
  /**
   * Waits for the process to finish.
   *
   * @return its exit code
   */
  int waitFor();

  /**
   * Whether the process is still running.
   *
   * @return {@code true} while it runs
   */
  boolean isAlive();

  /** Asks the process to terminate (SIGTERM on POSIX systems). */
  void destroy();
}
