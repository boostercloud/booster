package com.boostercloud.cli.commands;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.ConfigService;
import com.boostercloud.cli.services.ProjectChecker;
import java.nio.file.Path;
import picocli.CommandLine;

/** {@code boost clean}: removes the build output ({@code mvn clean}; {@code commands/clean.ts}). */
@CommandLine.Command(name = "clean", description = "Clean the current application as configured in your `pom.xml` file.")
public final class Clean extends VerboseCommand {
  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Clean(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    runTasks(ctx, cwd -> ConfigService.cleanProject(ctx, ctx.cwd()));
    return 0;
  }

  /**
   * Runs the clean script.
   *
   * @param ctx the CLI context
   * @param clean cleans the project
   */
  static void runTasks(CliContext ctx, Script.Action<Path> clean) {
    Script.init(ctx.oraLogger(), "boost " + Brand.dangerize("clean") + " 🚀", ctx::cwd)
        .step("Checking project structure", cwd -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Cleaning project", clean)
        .info("Clean complete!")
        .done();
  }
}
