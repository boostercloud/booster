package com.boostercloud.cli.commands.add;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.BaseCommand;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.MethodGenerator;
import com.boostercloud.cli.services.ProjectChecker;
import com.boostercloud.cli.services.generator.target.HasName;
import com.boostercloud.cli.services.generator.target.HasReaction;
import com.boostercloud.cli.services.generator.target.Parsing;
import com.boostercloud.cli.services.generator.target.ReactionEvent;
import com.boostercloud.cli.services.java.ClassDeclaration;
import com.boostercloud.cli.services.java.JavaSourceFile;
import java.util.List;
import picocli.CommandLine;

/**
 * {@code boost add:reducer}: appends one reducer method per event (and their imports) to an existing entity
 * ({@code commands/add/reducer.ts}).
 */
@CommandLine.Command(
    name = "add:reducer",
    description = "add new reducer to entity",
    customSynopsis = "add:reducer --entity Entity --event Event",
    footer = {
      "$ boost add:reducer --entity Post --event PostCreated",
      "$ boost add:reducer --entity Comment --event CommentUpdated CommentVoted"
    })
public final class Reducer extends BaseCommand {
  @CommandLine.Option(names = "--entity", required = true, paramLabel = "<value>", description = "an entity name")
  String entity;

  @CommandLine.Option(
      names = "--event",
      required = true,
      arity = "1..*",
      paramLabel = "<value>",
      description = "an event name")
  List<String> event;

  /** The information of the script. */
  record ReducerInfo(String name, List<ReactionEvent> events) {}

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Reducer(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    run(ctx, entity, event);
    return 0;
  }

  static String pluralize(String word, int count) {
    return count == 1 ? word : word + "s";
  }

  /**
   * Runs the script.
   *
   * @param ctx the CLI context
   * @param rawEntity the entity class name
   * @param rawEvents the event class names
   */
  public static void run(CliContext ctx, String rawEntity, List<String> rawEvents) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.energize("add:reducer") + " 🚧",
            () -> {
              HasName name = Parsing.parseName(rawEntity);
              HasReaction reaction = Parsing.parseReaction(rawEvents);
              return new ReducerInfo(name.name(), reaction.events());
            })
        .step("Verifying project", info -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Generating " + pluralize("reducer", rawEvents.size()), info -> generateReducerMethods(ctx, info))
        .info(pluralize("Reducer", rawEvents.size()) + " generated!")
        .done();
  }

  static void generateReducerMethods(CliContext ctx, ReducerInfo info) {
    JavaSourceFile entitySourceFile = MethodGenerator.getResourceSourceFile(ctx, info.name());
    ClassDeclaration entityClass = entitySourceFile.getClassOrThrow(info.name());
    entityClass.addMethods(MethodGenerator.generateReducers(info.name(), info.events()));
    entitySourceFile.fixMissingImports(MethodGenerator.projectIndex(ctx)).save();
  }
}
