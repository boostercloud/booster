package com.boostercloud.cli.commands.add;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ports of {@code test/commands/add/projection.test.ts} and {@code test/commands/add/reducer.test.ts}. */
class AddCommandsTest {
  @TempDir Path dir;

  private TestCli cli;

  private static final String READ_MODEL =
      """
      package com.example.mock.readmodels;

      import com.boostercloud.framework.core.decorators.ReadModel;
      import com.boostercloud.framework.types.concepts.UUID;

      @ReadModel(authorize = All.class)
      public record PostReadModel(UUID id, String title, String author) {}
      """;

  private static final String ENTITY =
      """
      package com.example.mock.entities;

      import com.boostercloud.framework.core.decorators.Entity;
      import com.boostercloud.framework.types.concepts.UUID;

      @Entity
      public record Post(UUID id, String title, String author) {}
      """;

  @BeforeEach
  void setUp() {
    TestProjects.create(dir, "4.1.0");
    TestProjects.write(TestProjects.source(dir, "readmodels/PostReadModel"), READ_MODEL);
    TestProjects.write(TestProjects.source(dir, "entities/Post"), ENTITY);
    TestProjects.write(
        TestProjects.source(dir, "events/PostCreated"), "package com.example.mock.events;\npublic record PostCreated() {}\n");
    TestProjects.write(
        TestProjects.source(dir, "events/CommentVoted"), "package com.example.mock.events;\npublic record CommentVoted() {}\n");
    cli = new TestCli(dir);
  }

  @Nested
  class Projections {
    @Test
    void initCallsCheckCurrentDirBoosterVersion() {
      assertThatCode(() -> new Projection(cli.context()).init()).doesNotThrowAnyException();
    }

    @Test
    void generatesProjectionCorrectly() {
      TestCli.Result result = cli.run("add:projection", "--read-model", "PostReadModel", "--entity", "Post:id");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout())
          .isEqualTo(
              """
              ℹ boost add:projection 🚧
              - Verifying project
              ✔ Verifying project
              - Generating projection
              ✔ Generating projection
              ℹ Projection generated!
              """);
      assertThat(TestProjects.read(TestProjects.source(dir, "readmodels/PostReadModel")))
          .isEqualTo(
              """
              package com.example.mock.readmodels;

              import com.boostercloud.framework.core.decorators.Projects;
              import com.boostercloud.framework.core.decorators.ReadModel;
              import com.boostercloud.framework.types.concepts.ProjectionResult;
              import com.boostercloud.framework.types.concepts.UUID;
              import com.example.mock.entities.Post;

              @ReadModel(authorize = All.class)
              public record PostReadModel(UUID id, String title, String author) {
                @Projects(entity = Post.class, joinKey = "id")
                public static ProjectionResult<PostReadModel> projectPost(Post entity, PostReadModel currentPostReadModel) {
                  return /* NEW PostReadModel HERE */;
                }
              }
              """);
    }

    @Test
    void withFlagsMissing() {
      TestCli.Result result = cli.run("add:projection");
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains("Missing required flag read-model");
    }

    @Test
    void withEmptyReadModel() {
      TestCli.Result result = cli.run("add:projection", "--entity", "Post:id", "--read-model");
      assertThat(result.stderr()).contains("--read-model expects a value");
    }

    @Test
    void withEmptyEntity() {
      TestCli.Result result = cli.run("add:projection", "--read-model", "PostReadModel", "--entity");
      assertThat(result.stderr()).contains("--entity expects a value");
    }

    @Test
    void whenTheEntityFlagIsMissing() {
      TestCli.Result result = cli.run("add:projection", "--read-model", "PostReadModel");
      assertThat(result.stderr()).contains("Missing required flag entity");
    }

    @Test
    void whenProjectionDoesNotHaveEntityId() {
      TestCli.Result result = cli.run("add:projection", "--read-model", "PostReadModel", "--entity", "Post");
      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.stderr())
          .contains("Error parsing projection Post. Projections must be in the form of <entity name>:<entity id>");
    }

    @Test
    void whenProjectionHasEmptyEntityIdOrName() {
      assertThat(cli.run("add:projection", "--read-model", "PostReadModel", "--entity", "Post:").stderr())
          .contains("Error parsing projection Post:. Projections must be in the form of <entity name>:<entity id>");
      assertThat(cli.run("add:projection", "--read-model", "PostReadModel", "--entity", ":id").stderr())
          .contains("Error parsing projection :id. Projections must be in the form of <entity name>:<entity id>");
    }

    @Test
    void whenSourceFileDoesNotExist() {
      TestCli.Result result = cli.run("add:projection", "--read-model", "CommentReadModel", "--entity", "Post:id");
      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.stdout()).contains("✖ Generating projection");
      assertThat(result.stderr())
          .contains("Could not find source file in project with the provided file name: CommentReadModel.java");
    }

    @Test
    void whenClassDoesNotExistInSourceFile() {
      TestProjects.write(
          TestProjects.source(dir, "readmodels/Other"), "package com.example.mock.readmodels;\ninterface Other {}\n");
      TestCli.Result result = cli.run("add:projection", "--read-model", "Other", "--entity", "Post:id");
      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.stderr()).contains("Expected to find a class named 'Other'.");
      assertThat(TestProjects.read(TestProjects.source(dir, "readmodels/Other"))).doesNotContain("projectPost");
    }
  }

  @Nested
  class Reducers {
    @Test
    void initCallsCheckCurrentDirBoosterVersion() {
      assertThatCode(() -> new Reducer(cli.context()).init()).doesNotThrowAnyException();
    }

    @Test
    void generatesOneReducer() {
      TestCli.Result result = cli.run("add:reducer", "--entity", "Post", "--event", "PostCreated");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).contains("- Generating reducer\n").contains("ℹ Reducer generated!");
      assertThat(TestProjects.read(TestProjects.source(dir, "entities/Post")))
          .contains("import com.example.mock.events.PostCreated;")
          .contains("import com.boostercloud.framework.core.decorators.Reduces;")
          .contains(
              """
                @Reduces(PostCreated.class)
                public static Post reducePostCreated(PostCreated event, Post currentPost) {
                  return /* NEW Post HERE */;
                }
              }
              """);
    }

    @Test
    void generatesSeveralReducersInOrder() {
      TestCli.Result result = cli.run("add:reducer", "--entity", "Post", "--event", "PostCreated", "CommentVoted", "Unknown");
      assertThat(result.stdout()).contains("- Generating reducers\n").contains("ℹ Reducers generated!");
      String entity = TestProjects.read(TestProjects.source(dir, "entities/Post"));
      assertThat(entity.indexOf("reducePostCreated")).isLessThan(entity.indexOf("reduceCommentVoted"));
      assertThat(entity).contains("reduceUnknown(Unknown event, Post currentPost)").doesNotContain("import Unknown");
      assertThat(entity).contains("import com.example.mock.events.CommentVoted;\nimport com.example.mock.events.PostCreated;\n");
    }

    @Test
    void displaysErrors() {
      assertThat(cli.run("add:reducer").stderr()).contains("Missing required flag entity").contains("Missing required flag event");
      assertThat(cli.run("add:reducer", "--entity", "Post", "--event").stderr()).contains("--event expects a value");
      assertThat(cli.run("add:reducer", "--event", "PostCreated", "--entity").stderr()).contains("--entity expects a value");
      TestCli.Result missing = cli.run("add:reducer", "--entity", "Comment", "--event", "PostCreated");
      assertThat(missing.exitCode()).isEqualTo(1);
      assertThat(missing.stderr()).contains("Could not find source file in project with the provided file name: Comment.java");
    }
  }

  @Test
  void pluralizes() {
    assertThat(Reducer.pluralize("reducer", 1)).isEqualTo("reducer");
    assertThat(Reducer.pluralize("reducer", 2)).isEqualTo("reducers");
    assertThat(Reducer.pluralize("Reducer", 0)).isEqualTo("Reducers");
  }
}
