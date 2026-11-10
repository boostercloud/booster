package com.boostercloud.cli.services;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.services.generator.target.Projection;
import com.boostercloud.cli.services.generator.target.ReactionEvent;
import com.boostercloud.cli.services.java.JavaSourceFile;
import com.boostercloud.cli.services.java.MethodDeclaration;
import com.boostercloud.cli.services.java.MethodDeclaration.Decorator;
import com.boostercloud.cli.services.java.MethodDeclaration.Parameter;
import com.boostercloud.cli.services.java.ProjectIndex;
import com.boostercloud.cli.services.project.ProjectLayout;
import java.util.List;

/**
 * Builds the reducer and projection methods added by {@code add:reducer} and {@code add:projection}
 * ({@code services/method-generator.ts}, which uses ts-morph).
 */
public final class MethodGenerator {
  private MethodGenerator() {}

  /**
   * Finds the source file of a resource anywhere in the project's source directory by its file name
   * ({@code <name>.java}; the first one in path order when there are several).
   *
   * @param ctx the CLI context
   * @param name the class name
   * @return the source file
   * @throws CliException {@code Could not find source file in project with the provided file name: <file>}
   */
  public static JavaSourceFile getResourceSourceFile(CliContext ctx, String name) {
    ProjectLayout layout = ProjectLayout.of(ctx.cwd());
    String sourceFileName = name + ".java";
    List<ProjectIndex.Entry> entries = projectIndex(ctx).find(name);
    if (entries.isEmpty()) {
      throw new CliException(
          "Could not find source file in project with the provided file name: " + sourceFileName
              + " (searched in " + layout.sourceDirectory() + ")");
    }
    return JavaSourceFile.read(entries.getFirst().path());
  }

  /**
   * The index of the project's classes, used to add imports.
   *
   * @param ctx the CLI context
   * @return the index of the source directory of the project in the current directory
   */
  public static ProjectIndex projectIndex(CliContext ctx) {
    return ProjectIndex.scan(ProjectLayout.of(ctx.cwd()).absoluteSourceDirectory());
  }

  /**
   * One reducer per event, in order:
   *
   * <pre>{@code
   * @Reduces(PostCreated.class)
   * public static Post reducePostCreated(PostCreated event, Post currentPost) {
   *   return /* NEW Post HERE * /;
   * }
   * }</pre>
   *
   * @param entity the entity class name
   * @param events the events
   * @return the method structures
   */
  public static List<MethodDeclaration> generateReducers(String entity, List<ReactionEvent> events) {
    return events.stream()
        .map(
            event ->
                new MethodDeclaration(
                    List.of(new Decorator("Reduces", List.of(event.eventName() + ".class"))),
                    "public",
                    true,
                    "reduce" + event.eventName(),
                    List.of(
                        new Parameter("event", event.eventName(), false),
                        new Parameter("current" + entity, entity, true)),
                    entity,
                    List.of("return /* NEW " + entity + " HERE */")))
        .toList();
  }

  /**
   * The projection of an entity into a read model:
   *
   * <pre>{@code
   * @Projects(entity = Post.class, joinKey = "id")
   * public static ProjectionResult<PostReadModel> projectPost(Post entity, PostReadModel currentPostReadModel) {
   *   return /* NEW PostReadModel HERE * /;
   * }
   * }</pre>
   *
   * @param readModel the read model class name
   * @param projection the entity and join key
   * @return the method structure
   */
  public static MethodDeclaration generateProjection(String readModel, Projection projection) {
    return new MethodDeclaration(
        List.of(
            new Decorator(
                "Projects",
                List.of("entity = " + projection.entityName() + ".class", "joinKey = \"" + projection.entityId() + "\""))),
        "public",
        true,
        "project" + projection.entityName(),
        List.of(
            new Parameter("entity", projection.entityName(), false),
            new Parameter("current" + readModel, readModel, true)),
        "ProjectionResult<" + readModel + ">",
        List.of("return /* NEW " + readModel + " HERE */"));
  }
}
