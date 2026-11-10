package com.boostercloud.cli.services;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.common.Errors;
import com.boostercloud.cli.services.packagemanager.PackageManagerError;
import com.boostercloud.cli.services.packagemanager.PackageManagerService;
import com.boostercloud.cli.services.project.ConfiguredEnvironments;
import com.boostercloud.cli.services.project.ProjectConfig;
import com.boostercloud.cli.services.project.ProjectLayout;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds the user project and reads what the CLI needs to run it ({@code services/config-service.ts}).
 *
 * <p>The TypeScript CLI compiles the project and then {@code require}s it to obtain its {@code BoosterConfig}. The
 * Java CLI never loads user code: it builds the project with Maven and reads its configuration statically (main
 * class and class path from the pom, environments and app names from the {@code Booster.configure} calls). The
 * application itself performs the provider operations when the CLI launches it.
 */
public final class ConfigService {
  private ConfigService() {}

  /**
   * Checks that the directory is a Booster project, cleans and builds it, and reads its configuration for the
   * selected environment.
   *
   * @param ctx the CLI context
   * @param userProjectPath the project directory
   * @return the project configuration
   * @throws CliException when the project is not valid, does not compile, or the environment is not configured
   */
  public static ProjectConfig compileProjectAndLoadConfig(CliContext ctx, Path userProjectPath) {
    ProjectChecker.checkItIsABoosterProject(userProjectPath);
    compileProject(ctx, userProjectPath);
    return readProjectConfig(ctx, userProjectPath);
  }

  /**
   * Cleans and builds a project ({@code mvn clean}, then {@code mvn package -DskipTests}).
   *
   * @param ctx the CLI context
   * @param projectPath the project directory
   * @throws CliException {@code [<name>] Project contains compilation errors:} followed by the Maven error
   */
  public static void compileProject(CliContext ctx, Path projectPath) {
    try {
      PackageManagerService packageManager = ctx.packageManager();
      packageManager.setProjectRoot(projectPath);
      cleanProjectEff(packageManager, projectPath);
      packageManager.build(List.of());
    } catch (PackageManagerError e) {
      throw Errors.guardError("Project contains compilation errors").apply(e.error());
    }
  }

  /**
   * Cleans a project ({@code mvn clean}).
   *
   * @param ctx the CLI context
   * @param projectPath the project directory
   * @throws CliException {@code [<name>] Could not clean project:} followed by the Maven error
   */
  public static void cleanProject(CliContext ctx, Path projectPath) {
    try {
      cleanProjectEff(ctx.packageManager(), projectPath);
    } catch (PackageManagerError e) {
      throw Errors.guardError("Could not clean project").apply(e.error());
    }
  }

  private static void cleanProjectEff(PackageManagerService packageManager, Path projectPath) {
    packageManager.setProjectRoot(projectPath);
    packageManager.runScript("clean", List.of());
  }

  /**
   * Reads the configuration of a project for the selected environment.
   *
   * @param ctx the CLI context
   * @param userProjectPath the project directory
   * @return the configuration
   */
  static ProjectConfig readProjectConfig(CliContext ctx, Path userProjectPath) {
    ProjectLayout layout = ProjectLayout.of(userProjectPath);
    ConfiguredEnvironments environments = ConfiguredEnvironments.scan(layout.absoluteSourceDirectory());
    String currentEnvironment = Environment.currentEnvironment(ctx);
    checkEnvironmentWasConfigured(environments, currentEnvironment);
    return new ProjectConfig(layout.projectDir(), currentEnvironment, layout, environments);
  }

  /**
   * Checks, in this order, that some environment is configured, that one is selected, and that the selected one
   * is configured. Checks that depend on the environment names are skipped when some name is not a literal.
   *
   * @param environments the environments found in the sources
   * @param currentEnvironment the selected environment, may be {@code null}
   * @throws CliException with the same messages as the TypeScript CLI
   */
  static void checkEnvironmentWasConfigured(ConfiguredEnvironments environments, String currentEnvironment) {
    if (environments.names().isEmpty() && !environments.hasDynamicNames()) {
      throw new CliException(
          "You haven't configured any environment. Please make sure you have at least one environment configured by"
              + " calling 'Booster.configure' method (normally done inside the 'config' package of your project)");
    }
    if (currentEnvironment == null || currentEnvironment.isEmpty()) {
      throw new CliException(
          "You haven't provided any environment. Please make sure you are using option '-e' with a valid environment"
              + " name");
    }
    if (!environments.hasDynamicNames() && !environments.names().contains(currentEnvironment)) {
      throw new CliException(
          "The environment '"
              + currentEnvironment
              + "' does not match any of the environments you used to configure your Booster project, which are: '"
              + String.join(", ", environments.names())
              + "'");
    }
  }
}
