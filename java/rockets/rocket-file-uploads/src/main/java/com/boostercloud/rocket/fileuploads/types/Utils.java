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

  /**
   * Java-only (for {@code deleteFile}, which TypeScript does not have): whether a file name names an entry of the
   * directory it is given with, so that the directory, the one checked against the patterns, is really the file's
   * folder. It must not be empty, {@code .} or {@code ..}, nor contain a {@code /} or {@code \} (a separator on
   * Windows, and turned into {@code /} by browsers and Blob Storage URLs on every platform).
   *
   * @param fileName a file name
   * @return whether it is a single path segment that does not move out of its directory
   */
  public static boolean isPlainFileName(String fileName) {
    return fileName != null && !fileName.isEmpty() && !fileName.equals(".") && !fileName.equals("..")
        && fileName.indexOf('/') < 0 && fileName.indexOf('\\') < 0;
  }
}
