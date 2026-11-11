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

/** {@code boost new:read-model}: generates a read model ({@code commands/new/read-model.ts}). */
@CommandLine.Command(name = "new:read-model", description = "create a new read model")
public final class ReadModel extends BaseCommand {
  @CommandLine.Option(
      names = {"-f", "--fields"},
      arity = "1..*",
      paramLabel = "<value>",
      description = "fields that this read model will contain")
  List<String> fields;

  @CommandLine.Option(
      names = {"-p", "--projects"},
      arity = "1..*",
      paramLabel = "<value>",
      description = "entities that this read model will project to build its state")
  List<String> projects;

  @CommandLine.Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "READMODELNAME",
      description = "name of the read model")
  String readModelName;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public ReadModel(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    List<String> rawFields = fields == null ? List.of() : fields;
    List<String> projections = projects == null ? List.of() : projects;
    if (readModelName == null || readModelName.isEmpty()) {
      ctx.err().println("You haven't provided a read model name, but it is required, run with --help for usage");
      return 0;
    }
    run(ctx, readModelName, rawFields, projections);
    return 0;
  }

  /**
   * Runs the generation script.
   *
   * @param ctx the CLI context
   * @param name the read model name
   * @param rawFields the {@code name:type} fields
   * @param rawProjections the {@code Entity:idField} projections
   */
  public static void run(CliContext ctx, String name, List<String> rawFields, List<String> rawProjections) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.energize("new:read-model") + " 🚧",
            () ->
                Parsing.joinParsers(
                    Parsing.parseName(name), Parsing.parseFields(rawFields), Parsing.parseProjections(rawProjections)))
        .step("Verifying project", info -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Creating new read model", info -> generateReadModel(ctx, info))
        .info("Read model generated!")
        .done();
  }

  /**
   * The imports of a read model: {@code @ReadModel} (and {@code @Projects} when it projects entities),
   * {@code UUID} (its id), {@code ProjectionResult} when it projects entities, and every projected entity, in flag
   * order.
   *
   * @param entitiesPackage the package of the project's entities
   * @param entityNames the projected entities
   * @return the import declarations
   */
  static List<ImportDeclaration> generateImports(String entitiesPackage, List<String> entityNames) {
    List<String> coreComponents = new ArrayList<>(List.of("ReadModel"));
    if (!entityNames.isEmpty()) {
      coreComponents.add("Projects");
    }
    List<ImportDeclaration> imports = new ArrayList<>();
    imports.add(new ImportDeclaration(FrameworkApi.DECORATORS, String.join(", ", coreComponents)));
    imports.add(
        new ImportDeclaration(FrameworkApi.CONCEPTS, entityNames.isEmpty() ? "UUID" : "UUID, ProjectionResult"));
    entityNames.forEach(entityName -> imports.add(new ImportDeclaration(entitiesPackage, entityName)));
    return imports;
  }

  @SuppressWarnings("unchecked")
  private static void generateReadModel(CliContext ctx, Map<String, Object> info) {
    List<Map<String, Object>> projections = (List<Map<String, Object>>) info.getOrDefault("projections", List.of());
    List<String> entityNames = projections.stream().map(p -> String.valueOf(p.get("entityName"))).toList();
    String entitiesPackage = ResourceGeneration.projectPackage(ctx, ResourcePackages.ENTITIES);
    ResourceGeneration.generate(
        ctx, ResourcePackages.READ_MODELS, TemplateType.READ_MODEL, generateImports(entitiesPackage, entityNames), info);
  }
}
