package com.boostercloud.integration.helper;

import com.boostercloud.application.tester.Sleep;
import com.boostercloud.integration.harness.E2eSettings;
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
 * File helpers of the integration suites. Port of {@code integration/helper/file-helper.ts}; paths are
 * {@link Path}s and relative ones resolve against the working directory, as in Node.
 */
public final class FileHelper {

  /** Directory of the fixtures, relative to the module ({@code integration/fixtures} in TypeScript). */
  public static final String FIXTURES_DIRECTORY = "src/test/resources/fixtures";
  /** Suffix of every sandbox directory (TypeScript: it makes the folder gitignored). */
  public static final String SANDBOX_SUFFIX = "-integration-sandbox";
  /** Name of the file holding the PID of a local server, inside its sandbox. */
  public static final String PID_FILE = "local_provider.pid";

  private FileHelper() {}

  /**
   * {@code loadFixture(fixturePath, replacements)}: the fixture under {@link #FIXTURES_DIRECTORY} of the module, with
   * every occurrence of each {@code [search, replace]} pair replaced, pair after pair.
   *
   * @param fixturePath the fixture path, relative to the fixtures directory
   * @param replacements the {@code [search, replace]} pairs, or {@code null}
   * @return the contents
   */
  public static String loadFixture(String fixturePath, List<List<String>> replacements) {
    return loadFixture(E2eSettings.moduleDirectory().resolve(FIXTURES_DIRECTORY), fixturePath, replacements);
  }

  /**
   * {@link #loadFixture(String, List)} with an explicit fixtures directory.
   *
   * @param fixturesDirectory the fixtures directory
   * @param fixturePath the fixture path, relative to it
   * @param replacements the {@code [search, replace]} pairs, or {@code null}
   * @return the contents
   */
  public static String loadFixture(Path fixturesDirectory, String fixturePath, List<List<String>> replacements) {
    String template = readFileContent(fixturesDirectory.resolve(fixturePath));
    if (replacements == null) {
      return template;
    }
    String contents = template;
    for (List<String> replacement : replacements) {
      // split(search).join(replace): a literal, global replacement
      contents = contents.replace(replacement.get(0), replacement.get(1));
    }
    return contents;
  }

  /**
   * @param filePath a file
   * @return its UTF-8 contents
   */
  public static String readFileContent(Path filePath) {
    try {
      return Files.readString(filePath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @param filePath a file
   * @param data its new UTF-8 contents
   */
  public static void writeFileContent(Path filePath, String data) {
    try {
      Files.writeString(filePath, data, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * {@code removeFiles(filePaths, ignoreErrors = false)}.
   *
   * @param filePaths the files to delete
   */
  public static void removeFiles(List<Path> filePaths) {
    removeFiles(filePaths, false);
  }

  /**
   * Deletes each file; a failure is rethrown unless {@code ignoreErrors}.
   *
   * @param filePaths the files to delete
   * @param ignoreErrors whether failures are ignored
   */
  public static void removeFiles(List<Path> filePaths, boolean ignoreErrors) {
    for (Path file : filePaths) {
      try {
        if (Files.isDirectory(file)) {
          throw new IOException("EISDIR: illegal operation on a directory, unlink '" + file + "'");
        }
        Files.delete(file);
      } catch (IOException e) {
        if (!ignoreErrors) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }

  /**
   * Creates the folder when it does not exist (its parent must exist, as with {@code mkdirSync}).
   *
   * @param folder the folder
   */
  public static void createFolder(Path folder) {
    if (!Files.exists(folder)) {
      try {
        Files.createDirectory(folder);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /**
   * Deletes each folder recursively. A folder the operating system reports as not empty yet is retried up to 5 times,
   * one second apart ({@code Retrying deletion of <path>, <n> retries remaining...}); any other failure, or running out
   * of retries, logs {@code Failed to delete <path>, skipping...}. It never throws: failing an integration test
   * because of this is not worth it.
   *
   * @param paths the folders
   */
  public static void removeFolders(List<Path> paths) {
    for (Path path : paths) {
      int retries = 5;
      while (retries > 0) {
        try {
          deleteRecursively(path);
          break;
        } catch (DirectoryNotEmptyException e) {
          retries--;
          if (retries > 0) {
            System.err.println("Retrying deletion of " + path + ", " + retries + " retries remaining...");
            Sleep.sleep(1000);
          } else {
            System.err.println("Failed to delete " + path + ", skipping...");
          }
        } catch (IOException | RuntimeException e) {
          System.err.println("Failed to delete " + path + ", skipping...");
          break;
        }
      }
    }
  }

  /**
   * @param path a path
   * @return whether it exists ({@code existsSync})
   */
  public static boolean fileExists(Path path) {
    return Files.exists(path);
  }

  /**
   * @param directory a directory
   * @return the names of its entries, sorted ({@code readdirSync})
   */
  public static List<String> dirContents(Path directory) {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @param sandboxName a sandbox name
   * @return {@code <sandboxName>-integration-sandbox}
   */
  public static String sandboxPathFor(String sandboxName) {
    return sandboxName + SANDBOX_SUFFIX;
  }

  /**
   * @param sandboxPath a sandbox directory
   * @return {@code <sandboxPath>/local_provider.pid}
   */
  public static Path pidForSandboxPath(Path sandboxPath) {
    return sandboxPath.resolve(PID_FILE);
  }

  /**
   * Writes the PID of the server of a sandbox (decimal, no newline).
   *
   * @param sandboxPath the sandbox directory
   * @param pid the process id
   */
  public static void storePIDFor(Path sandboxPath, long pid) {
    writeFileContent(pidForSandboxPath(sandboxPath), Long.toString(pid));
  }

  /**
   * @param sandboxPath the sandbox directory
   * @return the PID stored by {@link #storePIDFor}
   */
  public static long readPIDFor(Path sandboxPath) {
    return Long.parseLong(readFileContent(pidForSandboxPath(sandboxPath)).trim(), 10);
  }

  /** {@code rmSync(path, { recursive: true, force: true })}: a missing path is not an error. */
  static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    Files.walkFileTree(path, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
        deleteIfExists(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
        if (exception instanceof NoSuchFileException) {
          return FileVisitResult.CONTINUE;
        }
        throw exception;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
        if (exception != null && !(exception instanceof NoSuchFileException)) {
          throw exception;
        }
        deleteIfExists(directory);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static void deleteIfExists(Path path) throws IOException {
    Files.deleteIfExists(path);
  }
}
