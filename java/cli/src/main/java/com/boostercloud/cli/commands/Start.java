package com.boostercloud.cli.commands;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.ConfigService;
import com.boostercloud.cli.services.Environment;
import com.boostercloud.cli.services.ProviderService;
import com.boostercloud.cli.services.process.RunningProcess;
import com.boostercloud.cli.services.project.ProjectConfig;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import picocli.CommandLine;

/**
 * {@code boost start}: builds the project and runs the local server ({@code commands/start.ts}). The application
 * output is streamed to the terminal and Ctrl-C stops it; the exit code of the application becomes the exit code
 * of the CLI.
 */
@CommandLine.Command(name = "start", description = "Start local debug server.")
public final class Start extends VerboseCommand {
  /** The variable with the port of the local server ({@code BOOSTER_LOCAL_PORT} of framework-types). */
  public static final String BOOSTER_LOCAL_PORT = "BOOSTER_INTERNAL_LOCAL_PORT";

  @CommandLine.Option(
      names = {"-p", "--port"},
      paramLabel = "<value>",
      defaultValue = "3000",
      description = "port to run the local runtime on")
  int port;

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
  public Start(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    if (Environment.initializeEnvironment(ctx, ctx.logger(), environment)) {
      ctx.environment().put(BOOSTER_LOCAL_PORT, port != 0 ? Integer.toString(port) : "3000");
      RunningProcess application =
          runTasks(
              ctx,
              port,
              () -> ConfigService.compileProjectAndLoadConfig(ctx, ctx.cwd()),
              config -> ProviderService.startProvider(ctx, port, config));
      return application.waitFor();
    }
    return 0;
  }

  /**
   * Runs the start script.
   *
   * @param ctx the CLI context
   * @param port the port of the local server
   * @param loader builds the project and reads its configuration
   * @param runner starts the application
   * @return the running application
   */
  static RunningProcess runTasks(
      CliContext ctx, int port, Callable<ProjectConfig> loader, Function<ProjectConfig, RunningProcess> runner) {
    AtomicReference<RunningProcess> application = new AtomicReference<>();
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.canarize("debug") + " [" + Environment.currentEnvironment(ctx) + "] 🐛",
            loader)
        .step("Starting debug server on port " + port, config -> application.set(runner.apply(config)))
        .done();
    return application.get();
  }
}
