package com.boostercloud.cli.commands;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.ConfigService;
import com.boostercloud.cli.services.Environment;
import com.boostercloud.cli.services.ProviderService;
import com.boostercloud.cli.services.project.ProjectConfig;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * {@code boost deploy}: builds the project and runs its {@code deploy} operation for the selected environment
 * ({@code commands/deploy.ts}).
 */
@CommandLine.Command(
    name = "deploy",
    description = "Deploy the current application as configured in your `BoosterConfigurator` classes.")
public final class Deploy extends VerboseCommand {
  @CommandLine.Option(
      names = {"-e", "--environment"},
      paramLabel = "<value>",
      description = "environment configuration to run")
  String environment;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Deploy(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    if (Environment.initializeEnvironment(ctx, ctx.logger(), environment)) {
      runTasks(
          ctx,
          () -> ConfigService.compileProjectAndLoadConfig(ctx, ctx.cwd()),
          config -> ProviderService.deployToCloudProvider(ctx, config));
    }
    return 0;
  }

  /**
   * Runs the deploy script.
   *
   * @param ctx the CLI context
   * @param compileAndLoad builds the project and reads its configuration
   * @param deployer deploys the application
   */
  static void runTasks(CliContext ctx, Callable<ProjectConfig> compileAndLoad, Script.Action<ProjectConfig> deployer) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.dangerize("deploy") + " [" + Environment.currentEnvironment(ctx) + "] 🚀",
            compileAndLoad)
        .step("Deploying", deployer)
        .info("Deployment complete!")
        .done();
  }
}
