package com.boostercloud.cli.services.packagemanager;

import com.boostercloud.cli.services.process.ProcessService;

/** Maven installed on the {@code PATH} ({@code mvn}; {@code mvn.cmd} on Windows). */
public final class MavenPackageManager {
  private MavenPackageManager() {}

  /**
   * The Maven executable name for the current operating system.
   *
   * @param windows whether the CLI runs on Windows
   * @return {@code mvn} or {@code mvn.cmd}
   */
  public static String command(boolean windows) {
    return windows ? "mvn.cmd" : "mvn";
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
