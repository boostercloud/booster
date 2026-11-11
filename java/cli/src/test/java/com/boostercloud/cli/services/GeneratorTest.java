package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.services.generator.target.FileDir;
import com.boostercloud.cli.services.generator.target.Target;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Port of {@code test/services/generator.test.ts}. */
class GeneratorTest {
  @TempDir Path dir;

  private CliContext ctx;

  @BeforeEach
  void setUp() {
    TestProjects.create(dir, "4.1.0");
    ctx = new TestCli(dir).context();
  }

  private static Map<String, Object> view(String name) {
    return Map.of(
        "name", name,
        "packageName", "com.example.mock.things",
        "imports", List.of(Map.of("fqn", "com.boostercloud.framework.types.concepts.UUID")),
        "hasImports", true,
        "fields",
            List.of(
                Map.of("name", "name", "type", "String", "last", false),
                Map.of("name", "description", "type", "String", "last", true)),
        "hasFields", true,
        "events", List.of(Map.of("eventName", "ProductPurchased")),
        "projections", List.of(Map.of("entityName", "Product", "entityId", "id")),
        "event", "ProductPurchased");
  }

  @Nested
  class GeneratesFileFromDefaultTemplate {
    @ParameterizedTest
    @EnumSource(TemplateType.class)
    void everyTemplate(TemplateType type) {
      String placementDir = "src/main/java/com/example/mock/things";
      Target<Map<String, Object>> target =
          new Target<>("NewResource", ".java", placementDir, Generator.template(ctx, type), view("NewResource"));
      String rendered = Templates.render(target.template(), target.info());

      Generator.generate(ctx, target);

      Path file = dir.resolve(placementDir).resolve("NewResource.java");
      assertThat(file).exists();
      assertThat(TestProjects.read(file))
          .isEqualTo(rendered)
          .startsWith("package com.example.mock.things;")
          .contains("NewResource")
          .doesNotContain("-> Custom code");
    }

    @Test
    void command() {
      String rendered = Templates.render(Generator.template(ctx, TemplateType.COMMAND), view("NewCommand"));
      assertThat(rendered)
          .isEqualTo(
              """
              package com.example.mock.things;

              import com.boostercloud.framework.types.concepts.UUID;

              @Command(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
              public record NewCommand(
                  String name,
                  String description
              ) {
                public static void handle(NewCommand command, Register register) {
                  register.events(/* YOUR EVENT HERE */);
                }
              }
              """);
    }

    @Test
    void entity() {
      String rendered = Templates.render(Generator.template(ctx, TemplateType.ENTITY), view("NewEntity"));
      assertThat(rendered)
          .isEqualTo(
              """
              package com.example.mock.things;

              import com.boostercloud.framework.types.concepts.UUID;

              @Entity
              public record NewEntity(
                  UUID id,
                  String name,
                  String description
              ) {

                @Reduces(ProductPurchased.class)
                public static NewEntity reduceProductPurchased(ProductPurchased event, NewEntity currentNewEntity) {
                  return /* NEW NewEntity HERE */;
                }
              }
              """);
    }

    @Test
    void readModel() {
      String rendered = Templates.render(Generator.template(ctx, TemplateType.READ_MODEL), view("NewReadModel"));
      assertThat(rendered)
          .contains("@ReadModel(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)")
          .contains("  @Projects(entity = Product.class, joinKey = \"id\")\n")
          .contains(
              "  public static ProjectionResult<NewReadModel> projectProduct(Product entity, NewReadModel"
                  + " currentNewReadModel) {\n    return ProjectionResult.of(/* NEW NewReadModel HERE */);\n  }\n}\n");
    }

    @Test
    void eventHandler() {
      String rendered = Templates.render(Generator.template(ctx, TemplateType.EVENT_HANDLER), view("NewEventHandler"));
      assertThat(rendered)
          .endsWith(
              """
              @EventHandler(ProductPurchased.class)
              public class NewEventHandler {
                public static void handle(ProductPurchased event, Register register) {}
              }
              """);
    }

    @Test
    void typeWithoutImportsDoesNotLeaveBlankLines() {
      Map<String, Object> plain =
          Map.of("name", "Money", "packageName", "a.b", "imports", List.of(), "hasImports", false, "fields", List.of(), "hasFields", false);
      assertThat(Templates.render(Generator.template(ctx, TemplateType.TYPE), plain))
          .isEqualTo("package a.b;\n\npublic record Money() {}\n");
    }
  }

  @Nested
  class GeneratesFileFromCustomTemplate {
    @ParameterizedTest
    @EnumSource(TemplateType.class)
    void everyTemplate(TemplateType type) {
      TestProjects.write(
          dir.resolve("stubs").resolve(type.fileName()),
          "package {{{packageName}}};\n\n// -> Custom code in the " + type.id() + "!\npublic class {{{name}}} {}\n");
      String placementDir = "src/main/java/com/example/mock/things";
      Generator.generate(
          ctx, new Target<>("CustomResource", ".java", placementDir, Generator.template(ctx, type), view("CustomResource")));
      assertThat(TestProjects.read(dir.resolve(placementDir).resolve("CustomResource.java")))
          .isEqualTo(
              "package com.example.mock.things;\n\n// -> Custom code in the " + type.id() + "!\npublic class CustomResource {}\n");
    }

    @Test
    void usesTheBuiltInTemplateWhenThereIsNoCustomOne() {
      TestProjects.write(dir.resolve("stubs").resolve("command.stub"), "custom");
      assertThat(Generator.template(ctx, TemplateType.COMMAND)).isEqualTo("custom");
      assertThat(Generator.template(ctx, TemplateType.ENTITY)).isEqualTo(Templates.bundled("entity.stub"));
    }
  }

  @Test
  void validatesTheResourceNameAfterCheckingTheFile() {
    TestCli cli = new TestCli(dir);
    assertThatThrownBy(
            () -> Generator.generate(cli.context(), new Target<>("bad name", ".java", "src", "x", Map.of())))
        .hasMessage("'bad name' is not valid resource name. Please use PascalCase name with valid characters.");
    assertThat(cli.stdout()).contains("Checking if resource already exists...");
  }

  @Test
  void filePathAndResourceType() {
    assertThat(Generator.filePath(ctx, FileDir.of("Post", "src/main/java/x/entities", ".java")))
        .isEqualTo(dir.toAbsolutePath().normalize().resolve("src/main/java/x/entities/Post.java"));
    assertThat(Generator.getResourceType("src/main/java/x/readmodels")).isEqualTo("readmodels");
    assertThat(Generator.getResourceType("src/read-models")).isEqualTo("read-models");
    assertThat(Generator.getResourceType("a/b.c")).isEqualTo("b");
  }
}
