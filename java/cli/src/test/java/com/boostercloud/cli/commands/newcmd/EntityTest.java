package com.boostercloud.cli.commands.newcmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.services.TemplateType;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/commands/new/entity.test.ts}. */
class EntityTest extends GeneratorCommandTestBase {
  private static final String PACKAGE = "com.example.mock.entities";
  private static final String ENTITY = "Entity";
  private static final String REDUCES = "Reduces";
  private static final List<String> DEFAULT_IMPORTS =
      List.of("com.boostercloud.framework.core.decorators.Entity", "com.boostercloud.framework.types.concepts.UUID");
  private static final List<String> REDUCING_IMPORTS =
      List.of(
          "com.boostercloud.framework.core.decorators.Entity",
          "com.boostercloud.framework.core.decorators.Reduces",
          "com.boostercloud.framework.types.concepts.UUID",
          "com.example.mock.events.PostCreated");

  private static String renderEntity(List<String> imports, List<String[]> fields, List<String> events) {
    Map<String, Object> view = view(PACKAGE, "ExampleEntity", imports, fields);
    view.put("events", events.stream().map(e -> Map.of("eventName", e)).toList());
    return render(TemplateType.ENTITY, view);
  }

  @Test
  void initCallsCheckCurrentDirBoosterVersion() {
    assertThatCode(() -> new Entity(cli.context()).init()).doesNotThrowAnyException();
    TestProjects.write(dir.resolve("pom.xml"), TestProjects.pom("3.0.0"));
    assertThatThrownBy(() -> new Entity(new TestCli(dir).context()).init())
        .hasMessageContaining("Please upgrade your project Booster dependencies");
  }

  @Nested
  class CreatedCorrectly {
    @Test
    void withNoFieldsAndNoReduces() {
      TestCli.Result result = cli.run("new:entity", "ExampleEntity");
      assertThat(result.exitCode()).isZero();
      assertThat(read("entities/ExampleEntity")).isEqualTo(renderEntity(DEFAULT_IMPORTS, List.of(), List.of()));
      assertThat(read("entities/ExampleEntity"))
          .isEqualTo(
              """
              package com.example.mock.entities;

              import com.boostercloud.framework.core.decorators.Entity;
              import com.boostercloud.framework.types.concepts.UUID;

              @Entity
              public record ExampleEntity(
                  UUID id
              ) {
              }
              """);
      assertThat(result.stdout())
          .isEqualTo(
              """
              ℹ boost new:entity 🚧
              - Verifying project
              ✔ Verifying project
              - Creating new entity
              ℹ Checking if resource already exists...
              ✔ Creating new entity
              ℹ Entity generated!
              """);
    }

    @Test
    void withAStringField() {
      cli.run("new:entity", "ExampleEntity", "--fields", "title:string");
      assertThat(read("entities/ExampleEntity"))
          .isEqualTo(renderEntity(DEFAULT_IMPORTS, List.<String[]>of(new String[] {"title", "String"}), List.of()));
    }

    @Test
    void withAStringFieldReducingPostCreated() {
      cli.run("new:entity", "ExampleEntity", "--fields", "title:string", "--reduces", "PostCreated");
      assertThat(read("entities/ExampleEntity"))
          .isEqualTo(
              renderEntity(REDUCING_IMPORTS, List.<String[]>of(new String[] {"title", "String"}), List.of("PostCreated")));
    }

    @Test
    void withANumberField() {
      cli.run("new:entity", "ExampleEntity", "--fields", "quantity:number");
      assertThat(read("entities/ExampleEntity"))
          .isEqualTo(renderEntity(DEFAULT_IMPORTS, List.<String[]>of(new String[] {"quantity", "double"}), List.of()));
    }

    @Test
    void withAUuidFieldReducingPostCreated() {
      cli.run("new:entity", "ExampleEntity", "--fields", "identifier:UUID", "--reduces", "PostCreated");
      assertThat(read("entities/ExampleEntity"))
          .isEqualTo(
              renderEntity(REDUCING_IMPORTS, List.<String[]>of(new String[] {"identifier", "UUID"}), List.of("PostCreated")));
    }

    @Test
    void withMultipleFieldsReducingPostCreatedAndCommentCreated() {
      cli.run(
          "new:entity",
          "ExampleEntity",
          "--fields",
          "title:string",
          "quantity:number",
          "identifier:UUID",
          "--reduces",
          "PostCreated",
          "CommentCreated");
      List<String> imports = new ArrayList<>(REDUCING_IMPORTS);
      imports.add(3, "com.example.mock.events.CommentCreated");
      assertThat(read("entities/ExampleEntity"))
          .isEqualTo(
              renderEntity(
                  imports,
                  List.of(
                      new String[] {"title", "String"},
                      new String[] {"quantity", "double"},
                      new String[] {"identifier", "UUID"}),
                  List.of("PostCreated", "CommentCreated")));
      assertThat(read("entities/ExampleEntity"))
          .contains("public static ExampleEntity reducePostCreated(PostCreated event, ExampleEntity currentExampleEntity)")
          .contains("public static ExampleEntity reduceCommentCreated(CommentCreated event, ExampleEntity currentExampleEntity)");
    }

    @Test
    void shortFlags() {
      cli.run("new:entity", "ExampleEntity", "-f", "title:string", "-r", "PostCreated");
      assertThat(read("entities/ExampleEntity")).contains("String title").contains("@Reduces(PostCreated.class)");
    }
  }

  @Nested
  class DisplaysAnError {
    @Test
    void withEmptyEntityName() {
      TestCli.Result result = cli.run("new:entity");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stderr()).contains("You haven't provided an entity name, but it is required, run with --help for usage");
      assertThat(nothingGeneratedIn("entities")).isTrue();
    }

    @Test
    void withEmptyFields() {
      assertParseError(cli.run("new:entity", "ExampleEntity", "--fields"), "Flag --fields expects a value");
    }

    @Test
    void withEmptyReduces() {
      assertParseError(cli.run("new:entity", "ExampleEntity", "--fields", "title:string", "--reduces"), "--reduces expects a value");
    }

    @Test
    void withEmptyFieldsFollowedByReduces() {
      assertParseError(cli.run("new:entity", "ExampleEntity", "--fields", "--reduces"), "Flag --fields expects a value");
    }

    @Test
    void withAFieldWithoutTypeAndEmptyReduces() {
      assertParseError(cli.run("new:entity", "ExampleEntity", "--fields", "title", "--reduces"), "Flag --reduces expects a value");
    }

    @Test
    void withFieldWithNoType() {
      assertRuntimeError(cli.run("new:entity", "ExampleEntity", "--fields", "title"), "Error parsing field title");
      assertThat(nothingGeneratedIn("entities")).isTrue();
    }

    @Test
    void withNoFieldTypeAfterColon() {
      assertRuntimeError(cli.run("new:entity", "ExampleEntity", "--fields", "title:"), "Error parsing field title");
      assertThat(nothingGeneratedIn("entities")).isTrue();
    }

    @Test
    void withRepeatedFields() {
      assertRuntimeError(
          cli.run("new:entity", "ExampleEntity", "--fields", "title:string", "title:string", "quantity:number"),
          "Error parsing field title");
      assertThat(nothingGeneratedIn("entities")).isTrue();
    }

    @Test
    void withAnInvalidName() {
      assertRuntimeError(cli.run("new:entity", "exampleEntity"), "'exampleEntity' is not valid resource name");
    }
  }
}
