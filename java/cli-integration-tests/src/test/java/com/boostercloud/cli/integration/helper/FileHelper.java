package com.boostercloud.cli.integration.helper;

import com.boostercloud.application.tester.Sleep;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Stream;

/**
 * Port of {@code integration/helper/file-helper.ts}: fixtures, file contents and folders of the sandboxes.
 *
 * <p>Differences from TypeScript: fixtures are read from the test class path ({@code src/test/resources/fixtures})
 * instead of {@code integration/fixtures} relative to the working directory, and the replacements are pairs of
 * strings instead of two-element arrays. {@code storePIDFor}/{@code readPIDFor} are not ported: they belong to the
 * local start/stop suite, and {@link Boost.RunningBoost} keeps the processes it starts.
 */
public final class FileHelper {
  private FileHelper() {}

  /**
   * A literal replacement applied by {@link #loadFixture(String, Replacement...)}.
   *
   * @param search the text to look for
   * @param replacement what replaces every occurrence
   */
  public record Replacement(String search, String replacement) {
    /**
     * @param search the text to look for
     * @param replacement what replaces every occurrence
     * @return the replacement
     */
    public static Replacement of(String search, String replacement) {
      return new Replacement(search, replacement);
    }
  }

  /**
   * {@code loadFixture(fixturePath, replacements)}: reads {@code fixtures/<fixturePath>} and replaces, in order,
   * every occurrence of each search text ({@code split(search).join(replace)}).
   *
   * @param fixturePath the path below the fixtures directory
   * @param replacements the replacements, applied left to right
   * @return the expected contents
   */
  public static String loadFixture(String fixturePath, Replacement... replacements) {
    String template = readFileContent(CliTestEnvironment.fixturesDirectory().resolve(fixturePath));
    for (Replacement replacement : replacements) {
      template = template.replace(replacement.search(), replacement.replacement());
    }
    return template;
  }

  /**
   * @param filePath a file
   * @return its contents as UTF-8 ({@code readFileSync(filePath, 'utf-8')})
   */
  public static String readFileContent(Path filePath) {
    try {
      return Files.readString(filePath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Writes a file, creating its parent directories.
   *
   * @param filePath the file
   * @param data the contents
   */
  public static void writeFileContent(Path filePath, String data) {
    try {
      if (filePath.getParent() != null) {
        Files.createDirectories(filePath.getParent());
      }
      Files.writeString(filePath, data, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * {@code removeFiles(filePaths)}: deletes each file; a missing file fails, as {@code unlinkSync} does.
   *
   * @param filePaths the files
   */
  public static void removeFiles(List<Path> filePaths) {
    removeFiles(filePaths, false);
  }

  /**
   * {@code removeFiles(filePaths, ignoreErrors)}.
   *
   * @param filePaths the files
   * @param ignoreErrors whether failures are ignored
   */
  public static void removeFiles(List<Path> filePaths, boolean ignoreErrors) {
    for (Path file : filePaths) {
      try {
        Files.delete(file);
      } catch (IOException e) {
        if (!ignoreErrors) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }

  /**
   * {@code createFolder(folder)}: creates the folder when it does not exist.
   *
   * @param folder the folder
   */
  public static void createFolder(Path folder) {
    try {
      Files.createDirectories(folder);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * {@code removeFolders(paths)}: deletes each folder recursively, retrying up to 5 times (one second apart) while a
   * directory is not empty because something is still writing to it; never fails, it warns instead.
   *
   * @param paths the folders
   */
  public static void removeFolders(List<Path> paths) {
    for (Path path : paths) {
      int retries = 5;
      while (true) {
        try {
          deleteRecursively(path);
          break;
        } catch (DirectoryNotEmptyException e) {
          retries--;
          if (retries <= 0) {
            System.err.println("Failed to delete " + path + ", skipping...");
            break;
          }
          System.err.println("Retrying deletion of " + path + ", " + retries + " retries remaining...");
          Sleep.sleep(1000);
        } catch (IOException | UncheckedIOException e) {
          System.err.println("Failed to delete " + path + ", skipping...");
          break;
        }
      }
    }
  }

  /**
   * @param path a file or directory
   * @return whether it exists ({@code existsSync})
   */
  public static boolean fileExists(Path path) {
    return Files.exists(path);
  }

  /**
   * {@code dirContents(path)} ({@code readdirSync}): the names of the entries of a directory, sorted.
   *
   * @param directory the directory
   * @return the entry names
   */
  public static List<String> dirContents(Path directory) {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * {@code sandboxPathFor(sandboxName)}: {@code <sandboxes>/<sandboxName>-integration-sandbox}.
   *
   * @param sandboxName the name of the sandbox
   * @return its path (not created)
   */
  public static Path sandboxPathFor(String sandboxName) {
    return CliTestEnvironment.sandboxesDirectory().resolve(sandboxName + "-integration-sandbox");
  }

  /**
   * Copies a directory tree.
   *
   * @param source the directory to copy
   * @param target where to copy it (created)
   */
  public static void copyDirectory(Path source, Path target) {
    try (Stream<Path> files = Files.walk(source)) {
      for (Path file : (Iterable<Path>) files::iterator) {
        Path destination = target.resolve(source.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(destination);
        } else {
          Files.copy(file, destination);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    Files.walkFileTree(
        path,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            if (exc instanceof NoSuchFileException) {
              return FileVisitResult.CONTINUE;
            }
            throw exc;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null && !(exc instanceof NoSuchFileException)) {
              throw exc;
            }
            Files.deleteIfExists(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }
}
