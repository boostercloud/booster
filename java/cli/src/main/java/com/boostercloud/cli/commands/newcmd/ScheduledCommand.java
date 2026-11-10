package com.boostercloud.cli.commands.newcmd;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.BaseCommand;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.FrameworkApi;
import com.boostercloud.cli.services.ProjectChecker;
import com.boostercloud.cli.services.TemplateType;
import com.boostercloud.cli.services.generator.target.ImportDeclaration;
import com.boostercloud.cli.services.generator.target.Parsing;
import com.boostercloud.cli.services.project.ResourcePackages;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;

/** {@code boost new:scheduled-command}: generates a scheduled command ({@code commands/new/scheduled-command.ts}). */
@CommandLine.Command(
    name = "new:scheduled-command",
    description = "generate new scheduled command, write 'boost new:scheduled-command -h' to see options")
public final class ScheduledCommand extends BaseCommand {
  @CommandLine.Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "SCHEDULEDCOMMANDNAME",
      description = "name of the scheduled command")
  String scheduledCommandName;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public ScheduledCommand(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    if (scheduledCommandName == null || scheduledCommandName.isEmpty()) {
      ctx.err().println("You haven't provided a scheduled command name, but it is required, run with --help for usage");
      return 0;
    }
    run(ctx, scheduledCommandName);
    return 0;
  }

  /**
   * Runs the generation script.
   *
   * @param ctx the CLI context
   * @param name the scheduled command name
   */
  public static void run(CliContext ctx, String name) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.energize("new:scheduled-command") + " 🚧",
            () -> Parsing.joinParsers(Parsing.parseName(name)))
        .step("Verifying project", info -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Creating new scheduled command", info -> generateScheduledCommand(ctx, info))
        .info("Scheduled command generated!")
        .done();
  }

  /**
   * The imports of a scheduled command: the {@code @ScheduledCommand} annotation and {@code Register}.
   *
   * @return the import declarations
   */
  static List<ImportDeclaration> generateImports() {
    return List.of(
        new ImportDeclaration(FrameworkApi.DECORATORS, "ScheduledCommand"),
        new ImportDeclaration(FrameworkApi.CONCEPTS, "Register"));
  }

  private static void generateScheduledCommand(CliContext ctx, Map<String, Object> info) {
    ResourceGeneration.generate(
        ctx, ResourcePackages.SCHEDULED_COMMANDS, TemplateType.SCHEDULED_COMMAND, generateImports(), info);
  }
}
