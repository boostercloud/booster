package com.boostercloud.integration.helper;

import com.boostercloud.integration.harness.E2eSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * Sandbox projects of the integration suites. Port of {@code packages/cli/src/common/sandbox.ts}, which the TypeScript
 * setups import by relative path.
 *
 * <p>Difference from TypeScript: the sandbox does not get a copy of the sources and the package files
 * ({@code src}, {@code package.json}, {@code package-lock.json}, {@code tsconfig.json}). A Java application runs from
 * its build output ({@code target/classes} and {@code target/lib}, the CLI launch contract), so the harness launches
 * the module's build output with the sandbox as working directory; the sandbox holds the configured assets and the
 * {@code .booster} data of the local provider.
 */
public final class Sandbox {

  private Sandbox() {}

  /**
   * Recreates {@code sandboxPath} (deleting what was there) and copies each asset of the module (a directory
   * recursively, a file directly) to the same relative path inside it.
   *
   * @param sandboxPath the sandbox directory
   * @param assets the module-relative assets to copy, or {@code null}
   * @return {@code sandboxPath}
   */
  public static Path createSandboxProject(Path sandboxPath, List<String> assets) {
    return createSandboxProject(E2eSettings.moduleDirectory(), sandboxPath, assets);
  }

  /**
   * {@link #createSandboxProject(Path, List)} with an explicit project to copy the assets from.
   *
   * @param projectDirectory where the assets are
   * @param sandboxPath the sandbox directory
   * @param assets the assets to copy, relative to {@code projectDirectory}, or {@code null}
   * @return {@code sandboxPath}
   */
  public static Path createSandboxProject(Path projectDirectory, Path sandboxPath, List<String> assets) {
    try {
      FileHelper.deleteRecursively(sandboxPath);
      Files.createDirectories(sandboxPath);
      if (assets != null) {
        for (String asset : assets) {
          Path source = projectDirectory.resolve(asset);
          Path target = sandboxPath.resolve(asset);
          if (Files.isDirectory(source)) {
            copyDirectory(source, target);
          } else {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
          }
        }
      }
      return sandboxPath;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not create the sandbox project " + sandboxPath, e);
    }
  }

  /**
   * Deletes the sandbox ({@code rmSync(sandboxPath, { recursive: true, force: true })}).
   *
   * @param sandboxPath the sandbox directory
   */
  public static void removeSandboxProject(Path sandboxPath) {
    try {
      FileHelper.deleteRecursively(sandboxPath);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not remove the sandbox project " + sandboxPath, e);
    }
  }

  private static void copyDirectory(Path source, Path target) throws IOException {
    Files.walkFileTree(source, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
        Files.createDirectories(target.resolve(source.relativize(directory).toString()));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
        Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}
