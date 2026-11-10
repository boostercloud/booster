package com.boostercloud.integration.azurecontract;

import com.boostercloud.framework.provider.azure.infrastructure.helper.DeploySources;
import com.boostercloud.integration.harness.E2eSettings;
import com.boostercloud.integration.helper.FileHelper;
import com.boostercloud.integration.helper.Sandbox;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A sandbox project laid out like a project the {@code boost} CLI has built: the assets of the application
 * ({@link Sandbox#createSandboxProject}, the TypeScript {@code createSandboxProject(sandboxPathFor(name), assets)}) plus
 * the build output in {@code target/classes} (a copy of the module's) and {@code target/lib} (the module's runtime
 * jars, hard-linked when possible). Actions run as the CLI launches them (java/cli/README.md, "Launch"):
 * {@code java -cp target/classes:target/lib/* com.boostercloud.integration.Main <action>} with the sandbox as working
 * directory, so {@code Booster.start} finds the project in the sandbox ({@code target/classes} without its suffix) and
 * the Azure infrastructure packages exactly this class path.
 */
final class CliBuiltProject implements AutoCloseable {

  /** What {@link #run} returns. */
  record Launch(int exitCode, String output, Path logFile) {}

  private final Path directory;

  private CliBuiltProject(Path directory) {
    this.directory = directory;
  }

  /**
   * Recreates {@code target/e2e-projects/<name>-integration-sandbox} with the assets and the build output.
   *
   * @param name the sandbox name ({@code sandboxPathFor(name)})
   * @param assets the module-relative assets to copy
   * @return the project
   * @throws IllegalStateException when the module has not been packaged ({@code target/lib} missing)
   */
  static CliBuiltProject create(String name, List<String> assets) {
    Path classes = E2eSettings.classesDirectory();
    Path lib = E2eSettings.libDirectory();
    if (!Files.isDirectory(classes) || !Files.isDirectory(lib)) {
      throw new IllegalStateException("The application is not built: " + classes + " and " + lib + " must exist (run"
          + " mvn -pl framework-integration-tests -am verify, which packages the module before failsafe runs)");
    }
    Path directory = E2eSettings.projectsDirectory().resolve(FileHelper.sandboxPathFor(name));
    Sandbox.createSandboxProject(directory, assets);
    try {
      copyTree(classes, directory.resolve("target").resolve("classes"));
      Path targetLib = Files.createDirectories(directory.resolve("target").resolve("lib"));
      try (Stream<Path> jars = Files.list(lib)) {
        for (Path jar : jars.filter(path -> path.getFileName().toString().endsWith(".jar")).sorted().toList()) {
          Path link = targetLib.resolve(jar.getFileName().toString());
          try {
            Files.createLink(link, jar);
          } catch (IOException | UnsupportedOperationException notLinkable) {
            Files.copy(jar, link, StandardCopyOption.REPLACE_EXISTING);
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not lay out the project " + directory, e);
    }
    return new CliBuiltProject(directory);
  }

  /**
   * @return the project directory (absolute)
   */
  Path directory() {
    return directory;
  }

  /**
   * @return the class path the CLI launches the application with, relative to the project
   */
  static String classpath() {
    return "target/classes" + File.pathSeparator + "target/lib/*";
  }

  /**
   * The package sources of a run of this project: what {@code DeploySources.fromRuntime} gives inside a JVM launched by
   * the CLI (the class path of this project, wildcard expanded, and the configured assets).
   *
   * @param assets {@code config.assets}
   * @return the sources
   */
  DeploySources deploySources(List<String> assets) {
    return DeploySources.fromClasspath(directory, directory.resolve("target").resolve("classes") + File.pathSeparator
        + directory.resolve("target").resolve("lib") + File.separator + "*", assets);
  }

  /**
   * Launches the application with an action and waits for it to end.
   *
   * @param action {@code synth}, {@code deploy}, {@code nuke}...
   * @param environment variables added to the test JVM's environment
   * @param removed variables removed from it (so the machine running the tests cannot change the outcome)
   * @param timeout how long the action may take
   * @return the exit code and the output
   */
  Launch run(String action, Map<String, String> environment, Collection<String> removed, Duration timeout) {
    List<String> command = new ArrayList<>();
    command.add(E2eSettings.javaExecutable().toString());
    command.addAll(E2eSettings.appJvmOptions());
    command.add("-cp");
    command.add(classpath());
    command.add(E2eSettings.mainClass());
    command.add(action);
    Path logFile = E2eSettings.logsDirectory().resolve(directory.getFileName() + "-" + action + ".log");
    Process process = null;
    try {
      Files.createDirectories(logFile.getParent());
      ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
          .redirectOutput(logFile.toFile());
      removed.forEach(builder.environment()::remove);
      builder.environment().putAll(environment);
      process = builder.start();
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("'" + action + "' did not end within " + timeout + "; see " + logFile);
      }
      return new Launch(process.exitValue(), Files.readString(logFile, StandardCharsets.UTF_8), logFile);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not run '" + action + "' in " + directory, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while running '" + action + "'", e);
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  /** Deletes the project (kept with {@code -Dbooster.e2e.keepProjects=true}). */
  @Override
  public void close() {
    if (!E2eSettings.keepProjects()) {
      Sandbox.removeSandboxProject(directory);
    }
  }

  private static void copyTree(Path source, Path target) throws IOException {
    Files.walkFileTree(source, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
        Files.createDirectories(target.resolve(source.relativize(dir).toString()));
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
