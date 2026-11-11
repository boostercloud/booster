package com.boostercloud.cli.services.packagemanager;

import com.boostercloud.cli.services.process.ProcessError;
import com.boostercloud.cli.services.process.ProcessService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** The generic implementation shared by the Maven package managers ({@code services/package-manager/common.ts}). */
public final class Common {
  /** Maven flags added to every command: batch mode and errors-only output. */
  public static final List<String> MAVEN_FLAGS = List.of("-B", "-q");

  private Common() {}

  /** Runs a command of the package manager in the project directory. */
  @FunctionalInterface
  public interface ScopedRun {
    /**
     * Runs {@code <packageManagerCommand> <scriptName> [<subscriptName>] <args...>}.
     *
     * @param scriptName the first argument
     * @param subscriptName an optional second argument (may be {@code null})
     * @param args the remaining arguments
     * @return the formatted output of the command
     * @throws ProcessError when the command fails
     */
    String run(String scriptName, String subscriptName, List<String> args);
  }

  /**
   * Returns a function that executes a package-manager command in the project directory. The directory is read
   * from {@code projectDirRef}; when it is empty, it is set to the current directory first.
   *
   * @param processService runs the commands
   * @param packageManagerCommand the program and its fixed arguments, e.g. {@code [mvn, -B, -q]}
   * @param projectDirRef the mutable project directory
   * @return the scoped runner
   */
  public static ScopedRun makeScopedRun(
      ProcessService processService, List<String> packageManagerCommand, AtomicReference<Path> projectDirRef) {
    return (scriptName, subscriptName, args) -> {
      Path projectDir = projectDirRef.updateAndGet(dir -> dir == null ? processService.cwd() : dir);
      List<String> command = new ArrayList<>(packageManagerCommand);
      command.add(scriptName);
      if (subscriptName != null && !subscriptName.isEmpty()) {
        command.add(subscriptName);
      }
      args.stream().filter(arg -> !arg.isEmpty()).forEach(command::add);
      return processService.exec(command, projectDir);
    };
  }

  /**
   * Creates a Maven-based package manager.
   *
   * @param processService runs the commands
   * @param packageManagerCommand the Maven executable, e.g. {@code mvn} or {@code ./mvnw}
   * @return the service
   */
  public static PackageManagerService makePackageManager(
      ProcessService processService, String packageManagerCommand) {
    AtomicReference<Path> projectDirRef = new AtomicReference<>();
    List<String> command = new ArrayList<>();
    command.add(packageManagerCommand);
    command.addAll(MAVEN_FLAGS);
    ScopedRun run = makeScopedRun(processService, command, projectDirRef);
    return new PackageManagerService() {
      @Override
      public void setProjectRoot(Path projectRoot) {
        projectDirRef.set(projectRoot);
      }

      @Override
      public void installProductionDependencies() {
        try {
          run.run("dependency:resolve", null, List.of("-DincludeScope=runtime"));
        } catch (ProcessError e) {
          throw new InstallDependenciesError(e.error());
        }
      }

      @Override
      public void installAllDependencies() {
        try {
          run.run("dependency:resolve", null, List.of());
        } catch (ProcessError e) {
          throw new InstallDependenciesError(e.error());
        }
      }

      @Override
      public String runScript(String scriptName, List<String> args) {
        try {
          return run.run(scriptName, null, args);
        } catch (ProcessError e) {
          throw new RunScriptError(e.error());
        }
      }

      @Override
      public String build(List<String> args) {
        List<String> buildArgs = new ArrayList<>();
        buildArgs.add("-DskipTests");
        buildArgs.addAll(args);
        try {
          return run.run("package", null, buildArgs);
        } catch (ProcessError e) {
          throw new RunScriptError(e.error());
        }
      }
    };
  }
}
