package com.boostercloud.cli.services.java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.services.MethodGenerator;
import com.boostercloud.cli.services.generator.target.Projection;
import com.boostercloud.cli.services.generator.target.ReactionEvent;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The Java source tooling that replaces ts-morph: lexer, type mapping, project index and source editing. */
class JavaToolingTest {
  @TempDir Path dir;

  @Nested
  class Lexer {
    @Test
    void skipsCommentsAndKeepsLiterals() {
      var tokens =
          JavaLexer.tokenize(
              "// class Fake {\n/* record Nope { */ class A { String s = \"}\\\"{\"; char c = '}'; String t = \"\"\"\n  }{\n  \"\"\"; }");
      assertThat(tokens).extracting(JavaLexer.Token::text)
          .containsExactly(
              "class", "A", "{", "String", "s", "=", "\"}\\\"{\"", ";", "char", "c", "=", "'}'", ";", "String", "t",
              "=", "\"\"\"\n  }{\n  \"\"\"", ";", "}");
    }

    @Test
    void decodesStringLiterals() {
      assertThat(JavaLexer.stringValue("\"a\\\"b\\\\c\\n\\u0041\"")).isEqualTo("a\"b\\c\nA");
      assertThat(JavaLexer.stringValue("\"\"\"\n    hello\n    world\"\"\"")).isEqualTo("hello\nworld");
    }
  }

  @Nested
  class Types {
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = {
          "string|String",
          "number|double",
          "boolean|boolean",
          "UUID|UUID",
          "Date|Instant",
          "Array<string>|List<String>",
          "ReadonlyArray<number>|List<Double>",
          "number[]|List<Double>",
          "string[][]|List<List<String>>",
          "Array<Array<boolean>>|List<List<Boolean>>",
          "Record<string, number>|Map<String, Double>",
          "Map<string,Array<UUID>>|Map<String, List<UUID>>",
          "Address|Address",
          "Address[]|List<Address>",
          "int|int",
          "List<int>|List<Integer>",
          "Optional<string>|Optional<String>",
          "any|Object",
          "com.acme.Money|com.acme.Money",
          "Array<|Array<"
        })
    void mapsTypeScriptTypesToJava(String type, String expected) {
      assertThat(JavaTypes.toJava(type).rendered()).isEqualTo(expected);
    }

    @Test
    void keepsTypesItCannotParse() {
      assertThat(JavaTypes.toJava("string | undefined").rendered()).isEqualTo("string | undefined");
      assertThat(JavaTypes.toJava("string | undefined").imports()).isEmpty();
    }

    @Test
    void reportsImportsAndUnresolvedNames() {
      JavaTypes.JavaType type = JavaTypes.toJava("Record<string, Array<Address>>");
      assertThat(type.imports()).containsExactlyInAnyOrder("java.util.Map", "java.util.List");
      assertThat(type.unresolvedSimpleNames()).containsExactly("Address");
      assertThat(JavaTypes.toJava("Date").imports()).containsExactly("java.time.Instant");
      assertThat(JavaTypes.toJava("UUID").imports()).containsExactly("com.boostercloud.framework.types.concepts.UUID");
      assertThat(JavaTypes.toJava("BigDecimal").imports()).containsExactly("java.math.BigDecimal");
      assertThat(JavaTypes.toJava("String").imports()).isEmpty();
    }
  }

  @Nested
  class Names {
    @Test
    void validatesIdentifiersAndPackages() {
      assertThat(JavaNames.isIdentifier("Post")).isTrue();
      assertThat(JavaNames.isIdentifier("class")).isFalse();
      assertThat(JavaNames.isIdentifier("2x")).isFalse();
      assertThat(JavaNames.isPackageName("com.example")).isTrue();
      assertThat(JavaNames.isPackageName("com..example")).isFalse();
      assertThat(JavaNames.isPackageName("com.my-app")).isFalse();
      assertThat(JavaNames.isPackageName("")).isFalse();
    }

    @Test
    void derivesPackageSegments() {
      assertThat(JavaNames.packageSegment("my-store")).isEqualTo("mystore");
      assertThat(JavaNames.packageSegment("3d.shop")).isEqualTo("app3dshop");
      assertThat(JavaNames.packageSegment("new")).isEqualTo("newapp");
      assertThat(JavaNames.packageSegment("---")).isEqualTo("app");
    }

    @Test
    void escapesStringLiterals() {
      assertThat(JavaNames.escapeStringLiteral("a\"b\\c\n")).isEqualTo("a\\\"b\\\\c\\n");
    }
  }

  @Nested
  class Index {
    @Test
    void indexesTheTopLevelTypesOfTheSources() {
      TestProjects.write(dir.resolve("com/a/events/PostCreated.java"), "package com.a.events;\npublic record PostCreated() {}");
      TestProjects.write(dir.resolve("com/a/x/Post.java"), "package com.a.x;\nrecord Post() {}");
      TestProjects.write(dir.resolve("com/a/y/Post.java"), "package com.a.y;\nrecord Post() {}");
      TestProjects.write(dir.resolve("Root.java"), "class Root {}");
      ProjectIndex index = ProjectIndex.scan(dir);
      assertThat(index.uniqueQualifiedName("PostCreated")).contains("com.a.events.PostCreated");
      assertThat(index.uniqueQualifiedName("Post")).isEmpty();
      assertThat(index.find("Post")).hasSize(2);
      assertThat(index.uniqueQualifiedName("Root")).contains("Root");
      assertThat(ProjectIndex.scan(dir.resolve("missing")).find("Post")).isEmpty();
    }

    @Test
    void readsPackageDeclarations() {
      assertThat(ProjectIndex.packageOf("/* c */ @Deprecated package a.b.c;\nclass X {}")).isEqualTo("a.b.c");
      assertThat(ProjectIndex.packageOf("class X {}")).isEmpty();
    }
  }

  @Nested
  class SourceEditing {
    private static final String ENTITY =
        """
        package com.example.mock.entities;

        import com.boostercloud.framework.core.decorators.Entity;
        import com.boostercloud.framework.types.concepts.UUID;

        @Entity
        public record Post(
            UUID id,
            String title
        ) {
          public String upperTitle() {
            return "{" + title.toUpperCase() + "}";
          }
        }
        """;

    @Test
    void addsReducersAndTheirImports() {
      TestProjects.write(dir.resolve("com/example/mock/events/PostCreated.java"), "package com.example.mock.events;\nrecord PostCreated() {}");
      TestProjects.write(dir.resolve("com/example/mock/events/PostUpdated.java"), "package com.example.mock.events;\nrecord PostUpdated() {}");
      JavaSourceFile file = JavaSourceFile.of(dir.resolve("Post.java"), ENTITY);
      file.getClassOrThrow("Post")
          .addMethods(
              MethodGenerator.generateReducers(
                  "Post", List.of(new ReactionEvent("PostCreated"), new ReactionEvent("PostUpdated"))));
      file.fixMissingImports(ProjectIndex.scan(dir));
      assertThat(file.text())
          .isEqualTo(
              """
              package com.example.mock.entities;

              import com.boostercloud.framework.core.decorators.Entity;
              import com.boostercloud.framework.core.decorators.Reduces;
              import com.boostercloud.framework.types.concepts.UUID;
              import com.example.mock.events.PostCreated;
              import com.example.mock.events.PostUpdated;

              @Entity
              public record Post(
                  UUID id,
                  String title
              ) {
                public String upperTitle() {
                  return "{" + title.toUpperCase() + "}";
                }

                @Reduces(PostCreated.class)
                public static Post reducePostCreated(PostCreated event, Post currentPost) {
                  return /* NEW Post HERE */;
                }

                @Reduces(PostUpdated.class)
                public static Post reducePostUpdated(PostUpdated event, Post currentPost) {
                  return /* NEW Post HERE */;
                }
              }
              """);
    }

    @Test
    void addsMethodsToEmptyClassesAndFilesWithoutImports() {
      JavaSourceFile file = JavaSourceFile.of(dir.resolve("PostReadModel.java"), "package a.b;\n\npublic record PostReadModel(String id) {}\n");
      file.getClassOrThrow("PostReadModel").addMethod(MethodGenerator.generateProjection("PostReadModel", new Projection("Post", "id")));
      file.fixMissingImports(ProjectIndex.empty());
      assertThat(file.text())
          .isEqualTo(
              """
              package a.b;

              import com.boostercloud.framework.core.decorators.Projects;
              import com.boostercloud.framework.types.concepts.ProjectionResult;

              public record PostReadModel(String id) {
                @Projects(entity = Post.class, joinKey = "id")
                public static ProjectionResult<PostReadModel> projectPost(Post entity, PostReadModel currentPostReadModel) {
                  return ProjectionResult.of(/* NEW PostReadModel HERE */);
                }
              }
              """);
    }

    @Test
    void reusesTheIndentationOfTheClassAndSkipsVisibleTypes() {
      String source =
          "package a;\n\nimport com.boostercloud.framework.core.decorators.*;\nimport b.PostCreated;\n\nclass Post {\n    int x;\n}\n";
      JavaSourceFile file = JavaSourceFile.of(dir.resolve("Post.java"), source);
      file.getClassOrThrow("Post").addMethods(MethodGenerator.generateReducers("Post", List.of(new ReactionEvent("PostCreated"))));
      file.fixMissingImports(ProjectIndex.empty());
      assertThat(file.text())
          .isEqualTo(
              "package a;\n\nimport com.boostercloud.framework.core.decorators.*;\nimport b.PostCreated;\n\nclass Post {\n"
                  + "    int x;\n\n    @Reduces(PostCreated.class)\n"
                  + "    public static Post reducePostCreated(PostCreated event, Post currentPost) {\n"
                  + "        return /* NEW Post HERE */;\n    }\n}\n");
    }

    @Test
    void findsOnlyTopLevelClassesAndRecords() {
      JavaSourceFile file =
          JavaSourceFile.of(dir.resolve("X.java"), "class X { class Inner {} Object o = Inner.class; }\ninterface Iface {}");
      assertThat(file.findClass("X")).isPresent();
      assertThat(file.findClass("Inner")).isEmpty();
      assertThat(file.findClass("Iface")).isEmpty();
      assertThatThrownBy(() -> file.getClassOrThrow("Missing")).hasMessage("Expected to find a class named 'Missing'.");
    }

    @Test
    void savesTheFile() {
      Path path = dir.resolve("Saved.java");
      JavaSourceFile file = JavaSourceFile.of(path, "class Saved {}");
      file.getClassOrThrow("Saved").addMethod(MethodGenerator.generateReducers("Saved", List.of(new ReactionEvent("E"))).getFirst());
      file.save();
      assertThat(JavaSourceFile.read(path).text()).isEqualTo(file.text()).contains("reduceE");
    }
  }
}
