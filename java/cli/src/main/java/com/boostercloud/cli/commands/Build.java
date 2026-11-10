package com.boostercloud.cli.commands;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.ConfigService;
import com.boostercloud.cli.services.ProjectChecker;
import java.nio.file.Path;
import picocli.CommandLine;

/** {@code boost build}: cleans and builds the project with Maven ({@code commands/build.ts}). */
@CommandLine.Command(name = "build", description = "Build the current application as configured in your `pom.xml` file.")
public final class Build extends VerboseCommand {
  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Build(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    runTasks(ctx, cwd -> ConfigService.compileProject(ctx, ctx.cwd()));
    return 0;
  }

  /**
   * Runs the build script.
   *
   * @param ctx the CLI context
   * @param compileAndLoad builds the project
   */
  static void runTasks(CliContext ctx, Script.Action<Path> compileAndLoad) {
    Script.init(ctx.oraLogger(), "boost " + Brand.dangerize("build") + " 🚀", ctx::cwd)
        .step("Checking project structure", cwd -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Building project", compileAndLoad)
        .info("Build complete!")
        .done();
  }
}
