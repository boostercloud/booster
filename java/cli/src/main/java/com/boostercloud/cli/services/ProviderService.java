package com.boostercloud.cli.services;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.common.ProcessExitException;
import com.boostercloud.cli.services.process.RunningProcess;
import com.boostercloud.cli.services.project.ProjectConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Project-name validation and the provider operations {@code deploy}, {@code synth}, {@code nuke} and
 * {@code start} ({@code services/provider-service.ts}).
 *
 * <p>TypeScript calls the provider infrastructure in-process. In Java the user's application performs the
 * operation itself: the CLI launches
 * {@code java -cp target/classes:target/lib/* <mainClass> <operation> [args]} with {@code BOOSTER_ENV} set, and
 * {@code Booster.run} executes the operation through the provider configured for that environment.
 */
public final class ProviderService {
  /** {@code 64} (Lambda name limit) − {@code 23} ({@code -subscriptions-notifier}) − {@code 4} ({@code -app}). */
  public static final int MAX_PROJECT_NAME_LENGTH = 37;

  private static final Set<String> OPERATIONS = Set.of("deploy", "nuke", "start", "synth");

  private ProviderService() {}

  /**
   * Checks that a project (application) name can be used to name cloud resources and Maven artifacts. The checks
   * run in this order and the first failing one is reported: length, spaces, upper-case letters, underscores and
   * (Java only) characters that are not lower-case letters, digits, {@code -} or {@code .}.
   *
   * @param name the project name
   * @throws ForbiddenProjectName {@code Project name cannot <restriction>:\n\n    Found: '<name>'}
   */
  public static void assertNameIsCorrect(String name) {
    if (name.length() > MAX_PROJECT_NAME_LENGTH) {
      throw new ForbiddenProjectName(name, "be longer than " + MAX_PROJECT_NAME_LENGTH + " characters");
    }
    if (name.contains(" ")) {
      throw new ForbiddenProjectName(name, "contain spaces");
    }
    if (!name.toLowerCase(java.util.Locale.ROOT).equals(name)) {
      throw new ForbiddenProjectName(name, "contain uppercase letters");
    }
    if (name.contains("_")) {
      throw new ForbiddenProjectName(name, "contain underscore");
    }
    if (!name.matches("[a-z0-9.\\-]*")) {
      throw new ForbiddenProjectName(name, "contain characters other than lowercase letters, digits, '-' and '.'");
    }
  }

  /**
   * Deploys the application to the provider of the selected environment.
   *
   * @param ctx the CLI context
   * @param config the project configuration
   * @throws ProcessExitException when the application fails
   */
  public static void deployToCloudProvider(CliContext ctx, ProjectConfig config) {
    runToCompletion(ctx, supportedInfrastructureMethodOrDie("deploy", config), config);
  }

  /**
   * Generates the infrastructure templates of the selected environment without deploying them.
   *
   * @param ctx the CLI context
   * @param config the project configuration
   * @throws ProcessExitException when the application fails
   */
  public static void synthToProvider(CliContext ctx, ProjectConfig config) {
    runToCompletion(ctx, supportedInfrastructureMethodOrDie("synth", config), config);
  }

  /**
   * Removes every resource of the application in the selected environment.
   *
   * @param ctx the CLI context
   * @param config the project configuration
   * @throws ProcessExitException when the application fails
   */
  public static void nukeCloudProviderResources(CliContext ctx, ProjectConfig config) {
    runToCompletion(ctx, supportedInfrastructureMethodOrDie("nuke", config), config);
  }

  /**
   * Starts the application server and waits until it listens on the port (or finishes).
   *
   * @param ctx the CLI context
   * @param port the port of the local server
   * @param config the project configuration
   * @return the running application, which the caller waits for
   * @throws ProcessExitException when the application finishes with an error before it is ready
   */
  public static RunningProcess startProvider(CliContext ctx, int port, ProjectConfig config) {
    String operation = supportedInfrastructureMethodOrDie("start", config);
    ctx.oraLogger().pause();
    RunningProcess process = ctx.process().spawn(applicationCommand(ctx, config, operation, List.of("--port", String.valueOf(port))), config.projectPath());
    if (port <= 0) {
      return process;
    }
    while (process.isAlive()) {
      if (ctx.portProbe().isListening(port)) {
        return process;
      }
      try {
        Thread.sleep(ctx.pollInterval());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        process.destroy();
        throw new CliException("Interrupted while waiting for the application to start", e);
      }
    }
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw failure(operation, exitCode);
    }
    return process;
  }

  /**
   * The command line that runs an operation of the application.
   *
   * @param ctx the CLI context
   * @param config the project configuration
   * @param operation {@code start}, {@code deploy}, {@code synth}, {@code nuke} or an injected command
   * @param args the arguments of the operation
   * @return {@code [java, -cp, <class path>, <main class>, <operation>, <args>...]}
   */
  public static List<String> applicationCommand(
      CliContext ctx, ProjectConfig config, String operation, List<String> args) {
    List<String> command = new ArrayList<>();
    command.add(ctx.javaExecutable());
    command.add("-cp");
    command.add(String.join(ctx.windows() ? ";" : ":", config.classpath()));
    command.add(config.mainClass());
    if (operation != null && !operation.isEmpty()) {
      command.add(operation);
    }
    command.addAll(args);
    return command;
  }

  /**
   * Validates the application name (when the sources assign it literally) and returns the operation to run.
   * Whether the provider of the environment supports the operation is decided by the application, which fails
   * with a non-zero exit code otherwise.
   *
   * @param methodName the operation
   * @param config the project configuration
   * @return the operation
   */
  static String supportedInfrastructureMethodOrDie(String methodName, ProjectConfig config) {
    config.configuredAppName().ifPresent(ProviderService::assertNameIsCorrect);
    if (!OPERATIONS.contains(methodName)) {
      throw new CliException(
          "Attempted to perform the '"
              + methodName
              + "' operation with a provider that does not support this feature, please check your environment"
              + " configuration.");
    }
    return methodName;
  }

  private static void runToCompletion(CliContext ctx, String operation, ProjectConfig config) {
    ctx.oraLogger().pause();
    RunningProcess process = ctx.process().spawn(applicationCommand(ctx, config, operation, List.of()), config.projectPath());
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw failure(operation, exitCode);
    }
  }

  private static ProcessExitException failure(String operation, int exitCode) {
    return new ProcessExitException(
        "The application finished with exit code " + exitCode + " while performing the '" + operation + "' operation",
        exitCode);
  }

  /** A project name that cannot be used. */
  public static final class ForbiddenProjectName extends CliException {
    private static final long serialVersionUID = 1L;

    private final String projectName;
    private final String restrictionText;

    ForbiddenProjectName(String projectName, String restrictionText) {
      super("Project name cannot " + restrictionText + ":\n\n    Found: '" + projectName + "'");
      this.projectName = projectName;
      this.restrictionText = restrictionText;
    }

    /** @return the rejected name */
    public String projectName() {
      return projectName;
    }

    /** @return the restriction, e.g. {@code contain spaces} */
    public String restrictionText() {
      return restrictionText;
    }
  }
}
