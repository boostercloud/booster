package com.boostercloud.cli.commands.newcmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.boostercloud.cli.services.TemplateType;
import com.boostercloud.cli.testing.TestCli;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code test/commands/new/read-model.test.ts}, case by case. TypeScript checks that {@code fs.outputFile}
 * was (not) called with the read model path; here the file is read back, and {@code nothingGeneratedIn} checks that
 * an error leaves the {@code readmodels} package empty.
 */
class ReadModelTest extends GeneratorCommandTestBase {
  private static final String PACKAGE = "com.example.mock.readmodels";
  private static final String READ_MODEL_NAME = "ExampleReadModel";
  private static final String READ_MODEL = "readmodels/" + READ_MODEL_NAME;
  private static final List<String> DEFAULT_READ_MODEL_IMPORTS =
      List.of("com.boostercloud.framework.core.decorators.ReadModel", "com.boostercloud.framework.types.concepts.UUID");
  private static final List<String> PROJECTING_READ_MODEL_IMPORTS =
      List.of(
          "com.boostercloud.framework.core.decorators.Projects",
          "com.boostercloud.framework.core.decorators.ReadModel",
          "com.boostercloud.framework.types.concepts.ProjectionResult",
          "com.boostercloud.framework.types.concepts.UUID",
          "com.example.mock.entities.Post");
  private static final List<String> PROJECTING_TWO_READ_MODEL_IMPORTS =
      List.of(
          "com.boostercloud.framework.core.decorators.Projects",
          "com.boostercloud.framework.core.decorators.ReadModel",
          "com.boostercloud.framework.types.concepts.ProjectionResult",
          "com.boostercloud.framework.types.concepts.UUID",
          "com.example.mock.entities.Comment",
          "com.example.mock.entities.Post");
  private static final List<String[]> POST_ID = List.<String[]>of(new String[] {"Post", "id"});
  /** title:string, quantity:number, identifier:UUID, with their Java types. */
  private static final List<String[]> MULTIPLE_FIELDS =
      List.of(new String[] {"title", "String"}, new String[] {"quantity", "double"}, new String[] {"identifier", "UUID"});

  private static String renderReadModel(List<String> imports, List<String[]> fields, List<String[]> projections) {
    Map<String, Object> view = view(PACKAGE, READ_MODEL_NAME, imports, fields);
    view.put("projections", projections.stream().map(p -> Map.of("entityName", p[0], "entityId", p[1])).toList());
    return render(TemplateType.READ_MODEL, view);
  }

  private void assertGenerated(TestCli.Result result, String expected) {
    assertThat(result.exitCode()).as(result.stderr()).isZero();
    assertThat(read(READ_MODEL)).isEqualTo(expected);
  }

  @Test
  void initCallsCheckCurrentDirBoosterVersion() {
    assertThatCode(() -> new ReadModel(cli.context()).init()).doesNotThrowAnyException();
  }

  @Nested
  class CreatedCorrectly {
    @Test
    void withNoFieldsAndNoProjects() {
      assertGenerated(cli.run("new:read-model", READ_MODEL_NAME), renderReadModel(DEFAULT_READ_MODEL_IMPORTS, List.of(), List.of()));
      assertThat(read(READ_MODEL))
          .isEqualTo(
              """
              package com.example.mock.readmodels;

              import com.boostercloud.framework.core.decorators.ReadModel;
              import com.boostercloud.framework.types.concepts.UUID;

              @ReadModel(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
              public record ExampleReadModel(
                  UUID id
              ) {
              }
              """);
    }

    @Test
    void withAStringField() {
      assertGenerated(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "title:string"),
          renderReadModel(DEFAULT_READ_MODEL_IMPORTS, List.<String[]>of(new String[] {"title", "String"}), List.of()));
    }

    @Test
    void withAStringFieldProjectingPostId() {
      assertGenerated(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "title:string", "--projects", "Post:id"),
          renderReadModel(PROJECTING_READ_MODEL_IMPORTS, List.<String[]>of(new String[] {"title", "String"}), POST_ID));
      assertThat(read(READ_MODEL))
          .isEqualTo(
              """
              package com.example.mock.readmodels;

              import com.boostercloud.framework.core.decorators.Projects;
              import com.boostercloud.framework.core.decorators.ReadModel;
              import com.boostercloud.framework.types.concepts.ProjectionResult;
              import com.boostercloud.framework.types.concepts.UUID;
              import com.example.mock.entities.Post;

              @ReadModel(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
              public record ExampleReadModel(
                  UUID id,
                  String title
              ) {

                @Projects(entity = Post.class, joinKey = "id")
                public static ProjectionResult<ExampleReadModel> projectPost(Post entity, ExampleReadModel currentExampleReadModel) {
                  return ProjectionResult.of(/* NEW ExampleReadModel HERE */);
                }
              }
              """);
    }

    @Test
    void withANumberField() {
      assertGenerated(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "quantity:number"),
          renderReadModel(DEFAULT_READ_MODEL_IMPORTS, List.<String[]>of(new String[] {"quantity", "double"}), List.of()));
    }

    @Test
    void withANumberFieldProjectingPostId() {
      assertGenerated(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "quantity:number", "--projects", "Post:id"),
          renderReadModel(PROJECTING_READ_MODEL_IMPORTS, List.<String[]>of(new String[] {"quantity", "double"}), POST_ID));
    }

    @Test
    void withAUuidField() {
      assertGenerated(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "identifier:UUID"),
          renderReadModel(DEFAULT_READ_MODEL_IMPORTS, List.<String[]>of(new String[] {"identifier", "UUID"}), List.of()));
    }

    @Test
    void withAUuidFieldProjectingPostId() {
      assertGenerated(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "identifier:UUID", "--projects", "Post:id"),
          renderReadModel(PROJECTING_READ_MODEL_IMPORTS, List.<String[]>of(new String[] {"identifier", "UUID"}), POST_ID));
    }

    @Test
    void withMultipleFields() {
      assertGenerated(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "title:string", "quantity:number", "identifier:UUID"),
          renderReadModel(DEFAULT_READ_MODEL_IMPORTS, MULTIPLE_FIELDS, List.of()));
    }

    @Test
    void withMultipleFieldsProjectingPostId() {
      assertGenerated(
          cli.run(
              "new:read-model",
              READ_MODEL_NAME,
              "--fields",
              "title:string",
              "quantity:number",
              "identifier:UUID",
              "--projects",
              "Post:id"),
          renderReadModel(PROJECTING_READ_MODEL_IMPORTS, MULTIPLE_FIELDS, POST_ID));
    }

    @Test
    void withMultipleFieldsProjectingPostIdAndCommentId() {
      assertGenerated(
          cli.run(
              "new:read-model",
              READ_MODEL_NAME,
              "--fields",
              "title:string",
              "quantity:number",
              "identifier:UUID",
              "--projects",
              "Post:id",
              "Comment:id"),
          renderReadModel(
              PROJECTING_TWO_READ_MODEL_IMPORTS,
              MULTIPLE_FIELDS,
              List.of(new String[] {"Post", "id"}, new String[] {"Comment", "id"})));
      assertThat(read(READ_MODEL))
          .contains("  @Projects(entity = Post.class, joinKey = \"id\")\n")
          .contains("  @Projects(entity = Comment.class, joinKey = \"id\")\n")
          .contains("public static ProjectionResult<ExampleReadModel> projectComment(Comment entity, ExampleReadModel currentExampleReadModel)");
    }

    /** Java only: the join key is written as given (TypeScript only tests {@code id}). */
    @Test
    void withAnotherJoinKey() {
      assertThat(cli.run("new:read-model", READ_MODEL_NAME, "--projects", "Comment:postId").exitCode()).isZero();
      assertThat(read(READ_MODEL)).contains("  @Projects(entity = Comment.class, joinKey = \"postId\")\n");
    }
  }

  @Nested
  class DisplaysAnError {
    @Test
    void withEmptyReadModelName() {
      TestCli.Result result = cli.run("new:read-model");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stderr()).contains("You haven't provided a read model name");
      assertThat(nothingGeneratedIn("readmodels")).isTrue();
    }

    @Test
    void withEmptyFields() {
      assertParseError(cli.run("new:read-model", READ_MODEL_NAME, "--fields"), "--fields expects a value");
      assertParseError(cli.run("new:read-model", READ_MODEL_NAME, "--fields"), "Flag --fields expects a value");
      assertThat(nothingGeneratedIn("readmodels")).isTrue();
    }

    @Test
    void withEmptyProjectionAfterFields() {
      assertParseError(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "title:string", "--projects"), "--projects expects a value");
      assertThat(nothingGeneratedIn("readmodels")).isTrue();
    }

    @Test
    void withEmptyProjection() {
      assertParseError(cli.run("new:read-model", READ_MODEL_NAME, "--projects"), "Flag --projects expects a value");
      assertThat(nothingGeneratedIn("readmodels")).isTrue();
    }

    @Test
    void withFieldWithNoType() {
      assertRuntimeError(cli.run("new:read-model", READ_MODEL_NAME, "--fields", "title"), "Error parsing field title");
      assertThat(nothingGeneratedIn("readmodels")).isTrue();
    }

    @Test
    void withNoFieldTypeAfterColon() {
      assertRuntimeError(cli.run("new:read-model", READ_MODEL_NAME, "--fields", "title:"), "Error parsing field title");
      assertThat(nothingGeneratedIn("readmodels")).isTrue();
    }

    @Test
    void withProjectionWithNoEntityId() {
      assertRuntimeError(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "title:string", "--projects", "Post"),
          "Error parsing projection Post");
      assertThat(nothingGeneratedIn("readmodels")).isTrue();
    }

    @Test
    void withProjectionWithEmptyEntityId() {
      assertRuntimeError(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "title:string", "--projects", "Post:"),
          "Error parsing projection Post");
      assertThat(nothingGeneratedIn("readmodels")).isTrue();
    }

    @Test
    void withProjectionWithEmptyEntityName() {
      assertRuntimeError(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "title:string", "--projects", ":id"),
          "Error parsing projection :id");
      assertThat(nothingGeneratedIn("readmodels")).isTrue();
    }

    @Test
    void withRepeatedFields() {
      assertRuntimeError(
          cli.run("new:read-model", READ_MODEL_NAME, "--fields", "title:string", "title:string", "quantity:number"),
          "Fields cannot be duplicated");
      assertThat(nothingGeneratedIn("readmodels")).isTrue();
    }
  }
}
