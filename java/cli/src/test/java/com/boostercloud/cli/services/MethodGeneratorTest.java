package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.services.generator.target.Projection;
import com.boostercloud.cli.services.generator.target.ReactionEvent;
import com.boostercloud.cli.services.java.JavaSourceFile;
import com.boostercloud.cli.services.java.MethodDeclaration;
import com.boostercloud.cli.services.java.MethodDeclaration.Decorator;
import com.boostercloud.cli.services.java.MethodDeclaration.Parameter;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/services/method-generator.test.ts}. */
class MethodGeneratorTest {
  @TempDir Path dir;

  @Test
  void methodStructuresHaveTheTypeScriptKeys() {
    assertThat(Arrays.stream(MethodDeclaration.class.getRecordComponents()).map(RecordComponent::getName))
        .containsExactly("decorators", "scope", "isStatic", "name", "parameters", "returnType", "statements");
  }

  @Test
  void generatesProjection() {
    MethodDeclaration projection = MethodGenerator.generateProjection("PostReadModel", new Projection("Post", "id"));
    assertThat(projection)
        .isEqualTo(
            new MethodDeclaration(
                List.of(new Decorator("Projects", List.of("entity = Post.class", "joinKey = \"id\""))),
                "public",
                true,
                "projectPost",
                List.of(new Parameter("entity", "Post", false), new Parameter("currentPostReadModel", "PostReadModel", true)),
                "ProjectionResult<PostReadModel>",
                List.of("return ProjectionResult.of(/* NEW PostReadModel HERE */)")));
  }

  @Nested
  class GeneratesReducers {
    @Test
    void generatesSingleReducer() {
      List<MethodDeclaration> reducers = MethodGenerator.generateReducers("Post", List.of(new ReactionEvent("PostCreated")));
      assertThat(reducers)
          .containsExactly(
              new MethodDeclaration(
                  List.of(new Decorator("Reduces", List.of("PostCreated.class"))),
                  "public",
                  true,
                  "reducePostCreated",
                  List.of(new Parameter("event", "PostCreated", false), new Parameter("currentPost", "Post", true)),
                  "Post",
                  List.of("return /* NEW Post HERE */")));
    }

    @Test
    void generatesMultipleReducers() {
      List<MethodDeclaration> reducers =
          MethodGenerator.generateReducers(
              "Post", List.of(new ReactionEvent("PostCreated"), new ReactionEvent("PostUpdated")));
      assertThat(reducers).extracting(MethodDeclaration::name).containsExactly("reducePostCreated", "reducePostUpdated");
      assertThat(reducers).allSatisfy(r -> assertThat(r.returnType()).isEqualTo("Post"));
      assertThat(reducers.get(1).decorators()).containsExactly(new Decorator("Reduces", List.of("PostUpdated.class")));
    }
  }

  @Test
  void rendersMethods() {
    MethodDeclaration projection = MethodGenerator.generateProjection("PostReadModel", new Projection("Post", "id"));
    assertThat(projection.render("  ", "  "))
        .isEqualTo(
            "  @Projects(entity = Post.class, joinKey = \"id\")\n"
                + "  public static ProjectionResult<PostReadModel> projectPost(Post entity, PostReadModel"
                + " currentPostReadModel) {\n"
                + "    return ProjectionResult.of(/* NEW PostReadModel HERE */);\n"
                + "  }");
    assertThat(projection.referencedTypeNames()).containsExactly("Projects", "Post", "PostReadModel", "ProjectionResult");
  }

  @Nested
  class GetResourceSourceFile {
    @Test
    void shouldReturnSourceFile() {
      TestProjects.create(dir, "4.1.0");
      Path file = TestProjects.source(dir, "readmodels/PostReadModel");
      TestProjects.write(file, "package com.example.mock.readmodels;\n\npublic record PostReadModel() {}\n");
      JavaSourceFile sourceFile = MethodGenerator.getResourceSourceFile(new TestCli(dir).context(), "PostReadModel");
      assertThat(sourceFile.path()).isEqualTo(file);
      assertThat(sourceFile.packageName()).isEqualTo("com.example.mock.readmodels");
    }

    @Test
    void shouldThrowErrorIfSourceFileDoesNotExist() {
      TestProjects.create(dir, "4.1.0");
      assertThatThrownBy(() -> MethodGenerator.getResourceSourceFile(new TestCli(dir).context(), "PostReadModel"))
          .hasMessageStartingWith("Could not find source file in project with the provided file name: PostReadModel.java");
    }
  }
}
