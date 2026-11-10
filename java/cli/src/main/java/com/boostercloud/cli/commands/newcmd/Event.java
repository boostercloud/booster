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

/** {@code boost new:event}: generates an event ({@code commands/new/event.ts}). */
@CommandLine.Command(name = "new:event", description = "create a new event")
public final class Event extends BaseCommand {
  @CommandLine.Option(
      names = {"-f", "--fields"},
      arity = "1..*",
      paramLabel = "<value>",
      description = "field that this event will contain")
  List<String> fields;

  @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "EVENTNAME", description = "name of the event")
  String eventName;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Event(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    List<String> rawFields = fields == null ? List.of() : fields;
    if (eventName == null || eventName.isEmpty()) {
      ctx.err().println("You haven't provided an event name, but it is required, run with --help for usage");
      return 0;
    }
    run(ctx, eventName, rawFields);
    return 0;
  }

  /**
   * Runs the generation script.
   *
   * @param ctx the CLI context
   * @param name the event name
   * @param rawFields the {@code name:type} fields
   */
  public static void run(CliContext ctx, String name, List<String> rawFields) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.energize("new:event") + " 🚧",
            () -> Parsing.joinParsers(Parsing.parseName(name), Parsing.parseFields(rawFields)))
        .step("Verifying project", info -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Creating new event", info -> generateEvent(ctx, info))
        .info("Event generated!")
        .done();
  }

  /**
   * The imports of an event: the {@code @Event} annotation and {@code UUID} (returned by {@code entityID()}).
   *
   * @return the import declarations
   */
  static List<ImportDeclaration> generateImports() {
    return List.of(
        new ImportDeclaration(FrameworkApi.DECORATORS, "Event"), new ImportDeclaration(FrameworkApi.CONCEPTS, "UUID"));
  }

  private static void generateEvent(CliContext ctx, Map<String, Object> info) {
    ResourceGeneration.generate(ctx, ResourcePackages.EVENTS, TemplateType.EVENT, generateImports(), info);
  }
}
