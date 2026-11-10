package com.boostercloud.cli.services.packagemanager;

import java.nio.file.Path;
import java.util.List;

/**
 * The build tool of a user project ({@code services/package-manager/index.ts}). In TypeScript this is npm, pnpm,
 * yarn or Rush; a Java Booster project is built with Maven, either installed ({@code mvn}) or through the project's
 * Maven Wrapper ({@code ./mvnw}).
 *
 * <p>Every command runs with {@code -B -q} (batch mode, errors only) in the project root, which defaults to the
 * current directory until {@link #setProjectRoot} is called.
 */
public interface PackageManagerService {
  /**
   * Sets the directory where the next commands run.
   *
   * @param projectRoot the project directory
   */
  void setProjectRoot(Path projectRoot);

  /**
   * Resolves the runtime dependencies of the project ({@code mvn dependency:resolve -DincludeScope=runtime}).
   *
   * @throws InstallDependenciesError when Maven fails
   */
  void installProductionDependencies();

  /**
   * Resolves (downloads) all the dependencies of the project ({@code mvn dependency:resolve}).
   *
   * @throws InstallDependenciesError when Maven fails
   */
  void installAllDependencies();

  /**
   * Runs a lifecycle phase or plugin goal, the Maven equivalent of a {@code package.json} script
   * ({@code mvn <scriptName> <args>}), for example {@code clean}.
   *
   * @param scriptName the phase or goal
   * @param args extra arguments
   * @return the formatted output of the command
   * @throws RunScriptError when Maven fails
   */
  String runScript(String scriptName, List<String> args);

  /**
   * Builds the project without running its tests ({@code mvn package -DskipTests <args>}).
   *
   * @param args extra arguments
   * @return the formatted output of the command
   * @throws RunScriptError when Maven fails
   */
  String build(List<String> args);
}
