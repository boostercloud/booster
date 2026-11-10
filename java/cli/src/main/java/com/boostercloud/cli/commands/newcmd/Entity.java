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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;

/** {@code boost new:entity}: generates an entity ({@code commands/new/entity.ts}). */
@CommandLine.Command(name = "new:entity", description = "create a new entity")
public final class Entity extends BaseCommand {
  @CommandLine.Option(
      names = {"-f", "--fields"},
      arity = "1..*",
      paramLabel = "<value>",
      description = "fields that this entity will contain")
  List<String> fields;

  @CommandLine.Option(
      names = {"-r", "--reduces"},
      arity = "1..*",
      paramLabel = "<value>",
      description = "events that this entity will reduce to build its state")
  List<String> reduces;

  @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "ENTITYNAME", description = "name of the entity")
  String entityName;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Entity(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    List<String> rawFields = fields == null ? List.of() : fields;
    List<String> events = reduces == null ? List.of() : reduces;
    if (entityName == null || entityName.isEmpty()) {
      ctx.err().println("You haven't provided an entity name, but it is required, run with --help for usage");
      return 0;
    }
    run(ctx, entityName, rawFields, events);
    return 0;
  }

  /**
   * Runs the generation script.
   *
   * @param ctx the CLI context
   * @param name the entity name
   * @param rawFields the {@code name:type} fields
   * @param rawEvents the events the entity reduces
   */
  public static void run(CliContext ctx, String name, List<String> rawFields, List<String> rawEvents) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.energize("new:entity") + " 🚧",
            () ->
                Parsing.joinParsers(
                    Parsing.parseName(name), Parsing.parseFields(rawFields), Parsing.parseReaction(rawEvents)))
        .step("Verifying project", info -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Creating new entity", info -> generateEntity(ctx, info))
        .info("Entity generated!")
        .done();
  }

  /**
   * The imports of an entity: {@code @Entity} (and {@code @Reduces} when it reduces events), {@code UUID} (its
   * id) and every reduced event from the events package, in flag order.
   *
   * @param eventsPackage the package of the project's events
   * @param eventNames the reduced events
   * @return the import declarations
   */
  static List<ImportDeclaration> generateImports(String eventsPackage, List<String> eventNames) {
    List<String> coreComponents = new ArrayList<>(List.of("Entity"));
    if (!eventNames.isEmpty()) {
      coreComponents.add("Reduces");
    }
    List<ImportDeclaration> imports = new ArrayList<>();
    imports.add(new ImportDeclaration(FrameworkApi.DECORATORS, String.join(", ", coreComponents)));
    imports.add(new ImportDeclaration(FrameworkApi.CONCEPTS, "UUID"));
    eventNames.forEach(eventName -> imports.add(new ImportDeclaration(eventsPackage, eventName)));
    return imports;
  }

  @SuppressWarnings("unchecked")
  private static void generateEntity(CliContext ctx, Map<String, Object> info) {
    List<Map<String, Object>> events = (List<Map<String, Object>>) info.getOrDefault("events", List.of());
    List<String> eventNames = events.stream().map(e -> String.valueOf(e.get("eventName"))).toList();
    String eventsPackage = ResourceGeneration.projectPackage(ctx, ResourcePackages.EVENTS);
    ResourceGeneration.generate(
        ctx, ResourcePackages.ENTITIES, TemplateType.ENTITY, generateImports(eventsPackage, eventNames), info);
  }
}
