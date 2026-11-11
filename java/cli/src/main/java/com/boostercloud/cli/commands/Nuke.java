package com.boostercloud.cli.commands;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.ConfigService;
import com.boostercloud.cli.services.Environment;
import com.boostercloud.cli.services.Prompter;
import com.boostercloud.cli.services.ProviderService;
import com.boostercloud.cli.services.project.ProjectConfig;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * {@code boost nuke}: builds the project and runs its {@code nuke} operation, which removes every resource of the
 * selected environment, after the user confirms by typing the application name ({@code commands/nuke.ts}).
 */
@CommandLine.Command(
    name = "nuke",
    description = "Remove all resources used by the current application as configured in your `BoosterConfigurator`"
        + " classes.")
public final class Nuke extends VerboseCommand {
  @CommandLine.Option(
      names = {"-e", "--environment"},
      paramLabel = "<value>",
      description = "environment configuration to run")
  String environment;

  @CommandLine.Option(
      names = {"-f", "--force"},
      description =
          "Run nuke without asking for confirmation. Be EXTRA CAUTIOUS with this option, all your application data"
              + " will be irreversibly DELETED without confirmation.")
  boolean force;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Nuke(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    if (Environment.initializeEnvironment(ctx, ctx.logger(), environment)) {
      runTasks(
          ctx,
          askToConfirmRemoval(ctx.prompter(), force, () -> ConfigService.compileProjectAndLoadConfig(ctx, ctx.cwd())),
          config -> ProviderService.nukeCloudProviderResources(ctx, config));
    }
    return 0;
  }

  /**
   * Runs the nuke script.
   *
   * @param ctx the CLI context
   * @param compileAndLoad builds the project, reads its configuration and asks for confirmation
   * @param nuke removes the resources
   */
  static void runTasks(CliContext ctx, Callable<ProjectConfig> compileAndLoad, Script.Action<ProjectConfig> nuke) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.dangerize("nuke") + " [" + Environment.currentEnvironment(ctx) + "] 🧨",
            compileAndLoad)
        .step("Removing", nuke)
        .info("Removal complete!")
        .done();
  }

  /**
   * Wraps the configuration loader so that, unless {@code force} is set, the user must type the application name
   * once the project is built.
   *
   * @param prompter asks for the name
   * @param force whether to skip the confirmation
   * @param config loads the configuration
   * @return the confirming loader
   */
  static Callable<ProjectConfig> askToConfirmRemoval(Prompter prompter, boolean force, Callable<ProjectConfig> config) {
    if (force) {
      return config;
    }
    return () -> {
      ProjectConfig configuration = config.call();
      String appName =
          prompter.defaultOrPrompt(null, "Please, enter the app name to confirm deletion of all resources:");
      if (appName.equals(configuration.appName())) {
        return configuration;
      }
      throw new CliException("Wrong app name, stopping nuke!");
    };
  }
}
