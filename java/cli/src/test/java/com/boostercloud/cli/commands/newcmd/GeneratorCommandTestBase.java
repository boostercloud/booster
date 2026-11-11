package com.boostercloud.cli.commands.newcmd;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.services.TemplateType;
import com.boostercloud.cli.services.Templates;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Shared set-up of the {@code new:*} command tests: a Booster project in a temporary directory. */
abstract class GeneratorCommandTestBase {
  @TempDir Path dir;

  TestCli cli;

  @BeforeEach
  void createProject() {
    TestProjects.create(dir, "4.1.0");
    cli = new TestCli(dir);
  }

  Path resource(String relativeClass) {
    return TestProjects.source(dir, relativeClass);
  }

  String read(String relativeClass) {
    return TestProjects.read(resource(relativeClass));
  }

  boolean nothingGeneratedIn(String subpackage) {
    Path packageDir = dir.resolve("src/main/java/com/example/mock").resolve(subpackage);
    try (var files = Files.exists(packageDir) ? Files.list(packageDir) : java.util.stream.Stream.<Path>empty()) {
      return files.findAny().isEmpty();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /** The view the generators build, written by hand like the TypeScript tests do. */
  static Map<String, Object> view(String packageName, String name, List<String> imports, List<String[]> fields) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("name", name);
    view.put("packageName", packageName);
    view.put("imports", imports.stream().map(fqn -> Map.of("fqn", fqn)).toList());
    view.put("hasImports", !imports.isEmpty());
    List<Map<String, Object>> fieldViews = new ArrayList<>();
    for (int i = 0; i < fields.size(); i++) {
      fieldViews.add(Map.of("name", fields.get(i)[0], "type", fields.get(i)[1], "last", i == fields.size() - 1));
    }
    view.put("fields", fieldViews);
    view.put("hasFields", !fieldViews.isEmpty());
    return view;
  }

  static String render(TemplateType type, Map<String, Object> view) {
    return Templates.render(Templates.bundled(type.fileName()), view);
  }

  void assertParseError(TestCli.Result result, String message) {
    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.stderr()).contains(message);
  }

  void assertRuntimeError(TestCli.Result result, String message) {
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).contains(message).contains("(You can see the full error logs in ./errors.log)");
    assertThat(dir.resolve("errors.log")).exists();
  }
}
