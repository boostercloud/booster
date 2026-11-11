package com.boostercloud.cli.hooks.commandnotfound;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.testing.FakeProcessService;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The {@code command_not_found} hook ({@code hooks/command_not_found/custom-parse.ts}, which has no tests). */
class CustomParseTest {
  @TempDir Path dir;

  @BeforeEach
  void createCompiledProject() throws Exception {
    TestProjects.create(dir, "4.1.0");
    Files.createDirectories(dir.resolve("target/classes"));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "hello -e prod --name x | hello | prod | --name x",
        "-e prod | '' | prod | ''",
        "-e prod hello --flag | '' | prod | hello --flag",
        "hello --name x | hello | | --name x",
        "hello -e | hello | | -e"
      })
  void splitsTheCommandLineLikeTypeScript(String commandLine, String id, String environment, String argv) {
    List<String> tokens = List.of(commandLine.split(" "));
    CustomParse.HookArguments arguments = CustomParse.parse(tokens.getFirst(), tokens.subList(1, tokens.size()));
    assertThat(arguments.id()).isEqualTo(id);
    assertThat(arguments.environment()).isEqualTo(environment);
    assertThat(String.join(" ", arguments.argv())).isEqualTo(argv);
  }

  @Test
  void runsTheInjectedCommandOfTheApplication() {
    TestCli cli = new TestCli(dir);
    cli.process().spawnExitCode(5);
    TestCli.Result result = cli.run("hello", "-e", "prod", "--name", "x");
    assertThat(result.exitCode()).isEqualTo(5);
    FakeProcessService.Call call = cli.process().calls().getFirst();
    assertThat(call.spawned()).isTrue();
    assertThat(call.command()).endsWith("com.example.mock.Main", "hello", "--name", "x");
    assertThat(call.environment()).containsEntry("BOOSTER_ENV", "prod").containsEntry("BOOSTER_CLI_HOOK", "true");
    assertThat(cli.process().lines()).noneMatch(line -> line.startsWith("mvn"));
  }

  @Test
  void showsTheApplicationHelpWhenThereIsOnlyAnEnvironment() {
    TestCli cli = new TestCli(dir);
    assertThat(cli.run("-e", "prod").exitCode()).isZero();
    assertThat(cli.process().calls().getFirst().command()).endsWith("com.example.mock.Main", "--help");
  }

  @Test
  void requiresAnEnvironment() {
    TestCli cli = new TestCli(dir);
    TestCli.Result result = cli.run("hello");
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).contains("No environment set");
    assertThat(cli.process().calls()).isEmpty();
  }

  @Test
  void requiresACompiledApplication() throws Exception {
    Files.delete(dir.resolve("target/classes"));
    TestCli cli = new TestCli(dir).env("BOOSTER_ENV", "local");
    TestCli.Result result = cli.run("hello");
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).contains("The application has not been compiled yet").contains("boost build");
  }

  @Test
  void reportsUnknownCommandsOutsideAProject() {
    TestCli cli = new TestCli(dir.resolve("target")).env("BOOSTER_ENV", "local");
    TestCli.Result result = cli.run("biuld");
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).contains("command biuld not found");
  }
}
