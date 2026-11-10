package com.boostercloud.cli.services.packagemanager;

import com.boostercloud.cli.services.process.ProcessService;

/**
 * The Maven Wrapper of the project ({@code ./mvnw}; {@code mvnw.cmd} on Windows). The command is relative, so it
 * runs the wrapper of the directory where the command is executed (the project root).
 */
public final class MavenWrapperPackageManager {
  /** The wrapper script on POSIX systems. */
  public static final String WRAPPER = "mvnw";

  /** The wrapper script on Windows. */
  public static final String WINDOWS_WRAPPER = "mvnw.cmd";

  private MavenWrapperPackageManager() {}

  /**
   * The wrapper command for the current operating system.
   *
   * @param windows whether the CLI runs on Windows
   * @return {@code ./mvnw} or {@code mvnw.cmd}
   */
  public static String command(boolean windows) {
    return windows ? WINDOWS_WRAPPER : "./" + WRAPPER;
  }

  /**
   * Creates the service.
   *
   * @param processService runs the commands
   * @param windows whether the CLI runs on Windows
   * @return the package manager
   */
  public static PackageManagerService make(ProcessService processService, boolean windows) {
    return Common.makePackageManager(processService, command(windows));
  }
}
