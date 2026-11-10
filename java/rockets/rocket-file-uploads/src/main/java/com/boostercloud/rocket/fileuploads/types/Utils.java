package com.boostercloud.rocket.fileuploads.types;

import java.util.List;

/**
 * Port of {@code utils.ts}.
 */
public final class Utils {

  private Utils() {}

  /**
   * {@code isValidDirectory(sourceDirectoryPath, allowedDirectories)}: whether at least one pattern matches the
   * directory with minimatch 3 and its default options ({@link Minimatch}): case-sensitive, {@code *} within one
   * segment, {@code **} across segments, no dot files, braces, extglobs and negation; an empty list matches nothing.
   *
   * @param sourceDirectoryPath a directory path without a file name
   * @param allowedDirectories the allowed patterns
   * @return whether the directory is allowed
   */
  public static boolean isValidDirectory(String sourceDirectoryPath, List<String> allowedDirectories) {
    for (String allowedDirectory : allowedDirectories) {
      if (Minimatch.minimatch(sourceDirectoryPath, allowedDirectory)) {
        return true;
      }
    }
    return false;
  }
}
