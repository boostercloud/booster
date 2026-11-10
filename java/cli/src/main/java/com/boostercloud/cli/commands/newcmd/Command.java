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

/** {@code boost new:command}: generates a command ({@code commands/new/command.ts}). */
@CommandLine.Command(name = "new:command", description = "Generate new resource, write 'boost new' to see options")
public final class Command extends BaseCommand {
  @CommandLine.Option(
      names = {"-f", "--fields"},
      arity = "1..*",
      paramLabel = "<value>",
      description = "Field that this command will contain")
  List<String> fields;

  @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "COMMANDNAME", description = "name of the command")
  String commandName;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Command(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    List<String> rawFields = fields == null ? List.of() : fields;
    if (commandName == null || commandName.isEmpty()) {
      ctx.err().println("You haven't provided a command name, but it is required, run with --help for usage");
      return 0;
    }
    run(ctx, commandName, rawFields);
    return 0;
  }

  /**
   * Runs the generation script.
   *
   * @param ctx the CLI context
   * @param name the command name
   * @param rawFields the {@code name:type} fields
   */
  public static void run(CliContext ctx, String name, List<String> rawFields) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.energize("new:command") + " 🚧",
            () -> Parsing.joinParsers(Parsing.parseName(name), Parsing.parseFields(rawFields)))
        .step("Verifying project", info -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Creating new command", info -> generateCommand(ctx, info))
        .info("Command generated!")
        .done();
  }

  /**
   * The imports of a command: the {@code @Command} annotation, {@code Register} and, when a field uses it,
   * {@code UUID}.
   *
   * @param fields the fields
   * @return the import declarations
   */
  static List<ImportDeclaration> generateImports(List<Field> fields) {
    boolean commandUsesUUID = fields.stream().anyMatch(f -> usesUuid(f.type()));
    List<String> componentsFromBoosterTypes = new ArrayList<>(List.of("Register"));
    if (commandUsesUUID) {
      componentsFromBoosterTypes.add("UUID");
    }
    return List.of(
        new ImportDeclaration(FrameworkApi.DECORATORS, "Command"),
        new ImportDeclaration(FrameworkApi.CONCEPTS, String.join(", ", componentsFromBoosterTypes)));
  }

  /**
   * Whether a field type is exactly {@code UUID} (other types that contain it, such as {@code Array<UUID>}, get
   * their imports from the field-type mapping).
   *
   * @param type the raw field type
   * @return {@code true} for {@code UUID}
   */
  static boolean usesUuid(String type) {
    return "UUID".equals(type);
  }

  @SuppressWarnings("unchecked")
  static List<Field> fieldsOf(Map<String, Object> info) {
    List<Map<String, Object>> raw = (List<Map<String, Object>>) info.getOrDefault("fields", List.of());
    return raw.stream().map(m -> new Field(String.valueOf(m.get("name")), String.valueOf(m.get("type")))).toList();
  }

  private static void generateCommand(CliContext ctx, Map<String, Object> info) {
    ResourceGeneration.generate(
        ctx, ResourcePackages.COMMANDS, TemplateType.COMMAND, generateImports(fieldsOf(info)), info);
  }
}
