package com.boostercloud.cli.commands.newcmd;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.BaseCommand;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.ProjectChecker;
import com.boostercloud.cli.services.TemplateType;
import com.boostercloud.cli.services.generator.target.Parsing;
import com.boostercloud.cli.services.project.ResourcePackages;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;

/**
 * {@code boost new:type}: generates a plain type in the {@code common} package ({@code commands/new/type.ts}). Its
 * only imports are the ones its field types need.
 */
@CommandLine.Command(name = "new:type", description = "create a new type")
public final class Type extends BaseCommand {
  @CommandLine.Option(
      names = {"-f", "--fields"},
      arity = "1..*",
      paramLabel = "<value>",
      description = "field that this type will contain")
  List<String> fields;

  @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "TYPENAME", description = "name of the type")
  String typeName;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Type(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    List<String> rawFields = fields == null ? List.of() : fields;
    if (typeName == null || typeName.isEmpty()) {
      ctx.err().println("You haven't provided a type name, but it is required, run with --help for usage");
      return 0;
    }
    run(ctx, typeName, rawFields);
    return 0;
  }

  /**
   * Runs the generation script.
   *
   * @param ctx the CLI context
   * @param name the type name
   * @param rawFields the {@code name:type} fields
   */
  public static void run(CliContext ctx, String name, List<String> rawFields) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.energize("new:type") + " 🚧",
            () -> Parsing.joinParsers(Parsing.parseName(name), Parsing.parseFields(rawFields)))
        .step("Verifying project", info -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Creating new type", info -> generateType(ctx, info))
        .info("Type generated!")
        .done();
  }

  private static void generateType(CliContext ctx, Map<String, Object> info) {
    ResourceGeneration.generate(ctx, ResourcePackages.COMMON, TemplateType.TYPE, List.of(), info);
  }
}
