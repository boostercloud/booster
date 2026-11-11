package com.boostercloud.cli.commands.newcmd;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.BaseCommand;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.FrameworkApi;
import com.boostercloud.cli.services.ProjectChecker;
import com.boostercloud.cli.services.TemplateType;
import com.boostercloud.cli.services.generator.target.Field;
import com.boostercloud.cli.services.generator.target.ImportDeclaration;
import com.boostercloud.cli.services.generator.target.Parsing;
import com.boostercloud.cli.services.project.ResourcePackages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;

/** {@code boost new:query}: generates a query ({@code commands/new/query.ts}). */
@CommandLine.Command(name = "new:query", description = "generate new query resource, write 'boost new' to see options")
public final class Query extends BaseCommand {
  @CommandLine.Option(
      names = {"-f", "--fields"},
      arity = "1..*",
      paramLabel = "<value>",
      description = "field list that this query will contain")
  List<String> fields;

  @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "QUERYNAME", description = "name of the query")
  String queryName;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Query(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    List<String> rawFields = fields == null ? List.of() : fields;
    if (queryName == null || queryName.isEmpty()) {
      ctx.err().println("You haven't provided a query name, but it is required, run with --help for usage");
      return 0;
    }
    run(ctx, queryName, rawFields);
    return 0;
  }

  /**
   * Runs the generation script.
   *
   * @param ctx the CLI context
   * @param name the query name
   * @param rawFields the {@code name:type} fields
   */
  public static void run(CliContext ctx, String name, List<String> rawFields) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.energize("new:query") + " 🚧",
            () -> Parsing.joinParsers(Parsing.parseName(name), Parsing.parseFields(rawFields)))
        .step("Verifying project", info -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Creating new query", info -> generateQuery(ctx, info))
        .info("Query generated!")
        .done();
  }

  /**
   * The imports of a query: the {@code @Query} annotation, {@code QueryInfo} and, when a field is a {@code UUID},
   * {@code UUID}.
   *
   * @param fields the fields
   * @return the import declarations
   */
  static List<ImportDeclaration> generateImports(List<Field> fields) {
    boolean queryUsesUUID = fields.stream().anyMatch(f -> Command.usesUuid(f.type()));
    List<String> componentsFromBoosterTypes = new ArrayList<>(List.of("QueryInfo"));
    if (queryUsesUUID) {
      componentsFromBoosterTypes.add("UUID");
    }
    return List.of(
        new ImportDeclaration(FrameworkApi.DECORATORS, "Query"),
        new ImportDeclaration(FrameworkApi.CONCEPTS, String.join(", ", componentsFromBoosterTypes)));
  }

  private static void generateQuery(CliContext ctx, Map<String, Object> info) {
    ResourceGeneration.generate(
        ctx, ResourcePackages.QUERIES, TemplateType.QUERY, generateImports(Command.fieldsOf(info)), info);
  }
}
