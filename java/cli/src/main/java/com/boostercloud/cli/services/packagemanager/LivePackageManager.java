package com.boostercloud.cli.services.packagemanager;

import com.boostercloud.cli.services.filesystem.FileSystemService;
import com.boostercloud.cli.services.process.ProcessService;
import java.util.List;

/**
 * Infers the package manager from the contents of the current directory ({@code services/package-manager/
 * live.impl.ts}): the project's Maven Wrapper when the directory contains one, otherwise Maven.
 *
 * <p>As in TypeScript the inference is repeated every time a package manager is needed.
 */
public final class LivePackageManager {
  private LivePackageManager() {}

  /**
   * Creates the package manager for the current directory ({@code InferredPackageManager}).
   *
   * @param processService runs the commands and provides the current directory
   * @param fileSystemService lists the current directory
   * @param windows whether the CLI runs on Windows
   * @return the inferred package manager
   */
  public static PackageManagerService infer(
      ProcessService processService, FileSystemService fileSystemService, boolean windows) {
    List<String> contents = fileSystemService.readDirectoryContents(processService.cwd());
    String wrapper = windows ? MavenWrapperPackageManager.WINDOWS_WRAPPER : MavenWrapperPackageManager.WRAPPER;
    if (contents.contains(wrapper)) {
      return MavenWrapperPackageManager.make(processService, windows);
    }
    return MavenPackageManager.make(processService, windows);
  }
}
