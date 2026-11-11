package com.boostercloud.cli.commands.add;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.BaseCommand;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.MethodGenerator;
import com.boostercloud.cli.services.ProjectChecker;
import com.boostercloud.cli.services.generator.target.HasName;
import com.boostercloud.cli.services.generator.target.HasProjection;
import com.boostercloud.cli.services.generator.target.Parsing;
import com.boostercloud.cli.services.java.ClassDeclaration;
import com.boostercloud.cli.services.java.JavaSourceFile;
import picocli.CommandLine;

/**
 * {@code boost add:projection}: appends a projection method (and its imports) to an existing read model
 * ({@code commands/add/projection.ts}).
 */
@CommandLine.Command(
    name = "add:projection",
    description = "add new projection to read model",
    customSynopsis = "add:projection --read-model ReadModel --entity Entity:id",
    footer = {
      "$ boost add:projection --read-model PostReadModel --entity Post:id",
      "$ boost add:projection --read-model CommentReadModel --entity Comment:id"
    })
public final class Projection extends BaseCommand {
  @CommandLine.Option(names = "--read-model", required = true, paramLabel = "<value>", description = "read-model name")
  String readModel;

  @CommandLine.Option(names = "--entity", required = true, paramLabel = "<value>", description = "an entity name")
  String entity;

  /** The information of the script. */
  record ProjectionInfo(String name, com.boostercloud.cli.services.generator.target.Projection projection) {}

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Projection(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    run(ctx, readModel, entity);
    return 0;
  }

  /**
   * Runs the script.
   *
   * @param ctx the CLI context
   * @param rawReadModel the read model class name
   * @param rawProjection the {@code Entity:idField} projection
   */
  public static void run(CliContext ctx, String rawReadModel, String rawProjection) {
    Script.init(
            ctx.oraLogger(),
            "boost " + Brand.energize("add:projection") + " 🚧",
            () -> {
              HasName name = Parsing.parseName(rawReadModel);
              HasProjection projection = Parsing.parseProjectionField(rawProjection);
              return new ProjectionInfo(name.name(), projection.projection());
            })
        .step("Verifying project", info -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Generating projection", info -> generateProjectionMethod(ctx, info))
        .info("Projection generated!")
        .done();
  }

  static void generateProjectionMethod(CliContext ctx, ProjectionInfo info) {
    JavaSourceFile readModelSourceFile = MethodGenerator.getResourceSourceFile(ctx, info.name());
    ClassDeclaration readModelClass = readModelSourceFile.getClassOrThrow(info.name());
    readModelClass.addMethod(MethodGenerator.generateProjection(info.name(), info.projection()));
    readModelSourceFile.fixMissingImports(MethodGenerator.projectIndex(ctx)).save();
  }
}
