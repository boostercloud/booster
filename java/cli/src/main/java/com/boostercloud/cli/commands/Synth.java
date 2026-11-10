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
 * {@code boost synth}: builds the project and runs its {@code synth} operation, which generates the cloud
 * templates without deploying them ({@code commands/synth.ts}).
 */
@CommandLine.Command(name = "synth", description = "Generate the required cloud templates to deploy your app manually.")
public final class Synth extends VerboseCommand {
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
  public Synth(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    if (Environment.initializeEnvironment(ctx, ctx.logger(), environment)) {
      runTasks(
          ctx,
          () -> ConfigService.compileProjectAndLoadConfig(ctx, ctx.cwd()),
          config -> ProviderService.synthToProvider(ctx, config));
    }
    return 0;
  }

  /**
   * Runs the synth script.
   *
   * @param ctx the CLI context
   * @param compileAndLoad builds the project and reads its configuration
   * @param synther synthesizes the templates
   */
  static void runTasks(CliContext ctx, Callable<ProjectConfig> compileAndLoad, Script.Action<ProjectConfig> synther) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.dangerize("synth") + " [" + Environment.currentEnvironment(ctx) + "] 🚀",
            compileAndLoad)
        .step("Synth", synther)
        .info("Synth complete!")
        .done();
  }
}
