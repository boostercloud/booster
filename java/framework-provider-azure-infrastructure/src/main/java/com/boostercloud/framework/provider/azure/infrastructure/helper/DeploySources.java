package com.boostercloud.framework.provider.azure.infrastructure.helper;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * What goes into a function app package: the Java counterpart of the {@code .deploy} sandbox the TypeScript CLI
 * prepares (compiled code, production {@code node_modules}, assets).
 *
 * <ul>
 *   <li>class path directories (normally {@code target/classes}) are packed into the application jar
 *       ({@code <appName>.jar});
 *   <li>class path jars (normally {@code target/lib/*.jar}, the runtime dependencies the generated pom copies) go to
 *       {@code lib/};
 *   <li>assets ({@code config.assets}, files or directories relative to the project root) are copied with their
 *       relative paths.
 * </ul>
 *
 * By default these are the class path of the running application (the {@code boost} CLI launches it with
 * {@code target/classes} and {@code target/lib/*}), so the deployed code is exactly the code that runs the deploy.
 *
 * @param projectRoot the project root (assets are relative to it)
 * @param classpath the class path entries
 * @param assets the assets
 */
public record DeploySources(Path projectRoot, List<Path> classpath, List<Path> assets) {

  /** Canonical constructor: copies the lists. */
  public DeploySources {
    Objects.requireNonNull(projectRoot, "projectRoot");
    classpath = List.copyOf(classpath);
    assets = List.copyOf(assets);
  }

  /**
   * The sources of the running application: {@code java.class.path} (wildcard entries expanded) and the assets.
   *
   * @param projectRoot the project root
   * @param assets {@code config.assets}, or {@code null}
   * @return the sources
   */
  public static DeploySources fromRuntime(Path projectRoot, List<String> assets) {
    return fromClasspath(projectRoot, System.getProperty("java.class.path", ""), assets);
  }

  /**
   * @param projectRoot the project root
   * @param classpath a class path string ({@link File#pathSeparator}-separated)
   * @param assets the assets, or {@code null}
   * @return the sources (missing entries skipped)
   */
  public static DeploySources fromClasspath(Path projectRoot, String classpath, List<String> assets) {
    List<Path> entries = new ArrayList<>();
    for (String entry : classpath.split(File.pathSeparator)) {
      if (entry.isBlank()) {
        continue;
      }
      if (entry.endsWith("*")) {
        Path directory = Path.of(entry.substring(0, entry.length() - 1));
        if (Files.isDirectory(directory)) {
          try (Stream<Path> jars = Files.list(directory)) {
            jars.filter(path -> path.getFileName().toString().endsWith(".jar")).sorted().forEach(entries::add);
          } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + directory + ": " + e.getMessage(), e);
          }
        }
        continue;
      }
      Path path = Path.of(entry);
      if (Files.exists(path)) {
        entries.add(path.toAbsolutePath().normalize());
      }
    }
    List<Path> assetPaths = new ArrayList<>();
    if (assets != null) {
      for (String asset : assets) {
        assetPaths.add(Path.of(asset));
      }
    }
    return new DeploySources(projectRoot, entries, assetPaths);
  }

  /**
   * @return the class path directories (packed into the application jar)
   */
  public List<Path> classDirectories() {
    return classpath.stream().filter(Files::isDirectory).toList();
  }

  /**
   * @return the class path jars (copied to {@code lib/})
   */
  public List<Path> libraries() {
    return classpath.stream().filter(Files::isRegularFile).toList();
  }
}
