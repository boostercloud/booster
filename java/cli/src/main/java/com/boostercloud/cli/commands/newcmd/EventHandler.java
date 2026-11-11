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

/** {@code boost new:event-handler}: generates an event handler ({@code commands/new/event-handler.ts}). */
@CommandLine.Command(name = "new:event-handler", description = "create a new event handler")
public final class EventHandler extends BaseCommand {
  @CommandLine.Option(
      names = {"-e", "--event"},
      paramLabel = "<value>",
      description = "event that this event handler with handle")
  String event;

  @CommandLine.Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "EVENTHANDLERNAME",
      description = "name of the event handler")
  String eventHandlerName;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public EventHandler(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    if (eventHandlerName == null || eventHandlerName.isEmpty()) {
      ctx.err().println("You haven't provided an event handler name, but it is required, run with --help for usage");
      return 0;
    }
    if (event == null || event.isEmpty()) {
      ctx.err().println("You haven't provided an event, but it is required, run with --help for usage");
      return 0;
    }
    run(ctx, eventHandlerName, event);
    return 0;
  }

  /**
   * Runs the generation script.
   *
   * @param ctx the CLI context
   * @param name the event handler name
   * @param eventName the handled event
   */
  public static void run(CliContext ctx, String name, String eventName) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.energize("new:event-handler") + " 🚧",
            () -> Parsing.joinParsers(Parsing.parseName(name), Parsing.parseEvent(eventName)))
        .step("Verifying project", info -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Creating new event handler", info -> generateEventHandler(ctx, info))
        .info("Event handler generated!")
        .done();
  }

  /**
   * The imports of an event handler: the handled event, the {@code @EventHandler} annotation and {@code Register}.
   *
   * @param eventsPackage the package of the project's events
   * @param eventName the handled event
   * @return the import declarations
   */
  static List<ImportDeclaration> generateImports(String eventsPackage, String eventName) {
    return List.of(
        new ImportDeclaration(eventsPackage, eventName),
        new ImportDeclaration(FrameworkApi.DECORATORS, "EventHandler"),
        new ImportDeclaration(FrameworkApi.CONCEPTS, "Register"));
  }

  private static void generateEventHandler(CliContext ctx, Map<String, Object> info) {
    String eventsPackage = ResourceGeneration.projectPackage(ctx, ResourcePackages.EVENTS);
    ResourceGeneration.generate(
        ctx,
        ResourcePackages.EVENT_HANDLERS,
        TemplateType.EVENT_HANDLER,
        generateImports(eventsPackage, String.valueOf(info.get("event"))),
        info);
  }
}
