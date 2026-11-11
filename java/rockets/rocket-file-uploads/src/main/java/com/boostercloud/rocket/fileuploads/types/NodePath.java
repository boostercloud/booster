package com.boostercloud.rocket.fileuploads.types;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The parts of Node's {@code path} module the rocket uses, with Node's POSIX semantics ({@code path.parse(p).dir},
 * {@code path.join(...)}, {@code path.normalize(p)}): {@link java.nio.file.Path} normalizes differently (trailing
 * separators, empty strings, {@code ..} above the start) and the TypeScript code compares and returns these strings.
 *
 * <p>On Windows ({@code \} as file separator) {@link #parseDir(String)} also splits on {@code \}, as Node's win32
 * {@code path.parse} does; joining always uses {@code /}.
 */
public final class NodePath {

  private static final char SEPARATOR = File.separatorChar;

  private NodePath() {}

  /**
   * {@code path.parse(path).dir}: everything before the last separator that is not part of the trailing separators
   * ({@code client1/myfile.txt} → {@code client1}, {@code myfile.txt} → {@code ""}, {@code a/b/} → {@code a},
   * {@code /a} → {@code /}, {@code a//b} → {@code a/}).
   *
   * @param path a path
   * @return its directory part
   */
  public static String parseDir(String path) {
    if (path.isEmpty()) {
      return "";
    }
    boolean isAbsolute = isSeparator(path.charAt(0));
    int start = isAbsolute ? 1 : 0;
    int startPart = 0;
    boolean matchedSlash = true;
    for (int i = path.length() - 1; i >= start; --i) {
      if (isSeparator(path.charAt(i))) {
        if (!matchedSlash) {
          startPart = i + 1;
          break;
        }
        continue;
      }
      matchedSlash = false;
    }
    if (startPart > 0) {
      return path.substring(0, startPart - 1);
    }
    return isAbsolute ? "/" : "";
  }

  /**
   * {@code path.join(...paths)}: the non-empty arguments joined with {@code /} and normalized; {@code "."} when there
   * is nothing left.
   *
   * @param paths the paths
   * @return the joined path
   */
  public static String join(String... paths) {
    List<String> parts = new ArrayList<>();
    for (String path : paths) {
      if (!path.isEmpty()) {
        parts.add(path);
      }
    }
    if (parts.isEmpty()) {
      return ".";
    }
    return normalize(String.join("/", parts));
  }

  /**
   * {@code path.normalize(path)} (POSIX): resolves {@code .} and {@code ..} segments, collapses repeated separators
   * and keeps a trailing one.
   *
   * @param path a path
   * @return the normalized path ({@code "."} for an empty one)
   */
  public static String normalize(String path) {
    if (path.isEmpty()) {
      return ".";
    }
    boolean isAbsolute = path.charAt(0) == '/';
    boolean trailingSeparator = path.charAt(path.length() - 1) == '/';
    String normalized = normalizeString(path, !isAbsolute);
    if (normalized.isEmpty()) {
      if (isAbsolute) {
        return "/";
      }
      return trailingSeparator ? "./" : ".";
    }
    if (trailingSeparator) {
      normalized += "/";
    }
    return isAbsolute ? "/" + normalized : normalized;
  }

  private static boolean isSeparator(char c) {
    return c == '/' || c == SEPARATOR;
  }

  /** {@code normalizeString(path, allowAboveRoot, '/', isPosixPathSeparator)} of Node's {@code path.js}. */
  private static String normalizeString(String path, boolean allowAboveRoot) {
    String res = "";
    int lastSegmentLength = 0;
    int lastSlash = -1;
    int dots = 0;
    char code = 0;
    for (int i = 0; i <= path.length(); ++i) {
      if (i < path.length()) {
        code = path.charAt(i);
      } else if (code == '/') {
        break;
      } else {
        code = '/';
      }
      if (code == '/') {
        if (lastSlash == i - 1 || dots == 1) {
          // NOOP
        } else if (dots == 2) {
          if (res.length() < 2 || lastSegmentLength != 2 || res.charAt(res.length() - 1) != '.'
              || res.charAt(res.length() - 2) != '.') {
            if (res.length() > 2) {
              int lastSlashIndex = res.lastIndexOf('/');
              if (lastSlashIndex == -1) {
                res = "";
                lastSegmentLength = 0;
              } else {
                res = res.substring(0, lastSlashIndex);
                lastSegmentLength = res.length() - 1 - res.lastIndexOf('/');
              }
              lastSlash = i;
              dots = 0;
              continue;
            } else if (!res.isEmpty()) {
              res = "";
              lastSegmentLength = 0;
              lastSlash = i;
              dots = 0;
              continue;
            }
          }
          if (allowAboveRoot) {
            res += res.isEmpty() ? ".." : "/..";
            lastSegmentLength = 2;
          }
        } else {
          if (!res.isEmpty()) {
            res += "/" + path.substring(lastSlash + 1, i);
          } else {
            res = path.substring(lastSlash + 1, i);
          }
          lastSegmentLength = i - lastSlash - 1;
        }
        lastSlash = i;
        dots = 0;
      } else if (code == '.' && dots != -1) {
        ++dots;
      } else {
        dots = -1;
      }
    }
    return res;
  }
}
