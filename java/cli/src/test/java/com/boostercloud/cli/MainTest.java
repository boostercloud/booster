package com.boostercloud.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Routing, help and version output of the {@code boost} executable. */
class MainTest {
  @TempDir Path dir;

  @Test
  void showsTheRootHelpWithoutArguments() {
    TestCli.Result result = new TestCli(dir).run();
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout())
        .startsWith("CLI of the Booster Framework, the next level of abstraction for cloud-native applications\n\nVERSION\n  booster-cli/")
        .contains("USAGE\n  $ boost [COMMAND]\n")
        .contains("TOPICS\n  add ")
        .contains("  new   Create a new project")
        .contains("  stub  ")
        .contains("COMMANDS\n  build ")
        .contains("  deploy  Deploy the current application")
        .contains("  help    Display help for boost.")
        .contains("  start   Start local debug server.");
    assertThat(new TestCli(dir).run("--help").stdout()).isEqualTo(result.stdout());
    assertThat(new TestCli(dir).run("help").stdout()).isEqualTo(result.stdout());
  }

  @Test
  void printsTheVersion() {
    TestCli.Result result = new TestCli(dir).run("--version");
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).startsWith("booster-cli/" + CliVersion.VERSION + " ").contains(" java-");
    assertThat(CliVersion.VERSION).matches("\\d+\\.\\d+\\.\\d+.*");
  }

  @Test
  void showsTheHelpOfACommandWithoutCheckingTheProject() {
    for (String[] args :
        new String[][] {{"new:entity", "--help"}, {"new:entity", "-h"}, {"help", "new:entity"}, {"new:entity", "Post", "--help"}}) {
      TestCli.Result result = new TestCli(dir).run(args);
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout())
          .isEqualTo(
              """
              create a new entity

              USAGE
                $ boost new:entity [ENTITYNAME] [-f <value>...] [-h] [-r <value>...]

              ARGUMENTS
                ENTITYNAME  name of the entity

              FLAGS
                -f, --fields=<value>...   fields that this entity will contain
                -h, --help                Show CLI help.
                -r, --reduces=<value>...  events that this entity will reduce to build its state
              """);
    }
  }

  @Test
  void showsDefaultsRequiredFlagsAndExamples() {
    assertThat(new TestCli(dir).run("start", "--help").stdout())
        .contains("  -p, --port=<value>         port to run the local runtime on [default: 3000]\n")
        .contains("      --verbose              display full error messages\n");
    assertThat(new TestCli(dir).run("add:projection", "--help").stdout())
        .contains("USAGE\n  $ boost add:projection --read-model ReadModel --entity Entity:id\n")
        .contains("      --read-model=<value>  read-model name (required)\n")
        .contains("EXAMPLES\n  $ boost add:projection --read-model PostReadModel --entity Post:id\n");
  }

  @Test
  void showsTheHelpOfATopic() {
    TestCli.Result result = new TestCli(dir).run("new");
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout())
        .contains("USAGE\n  $ boost new:COMMAND\n")
        .contains("  new:command            Generate new resource, write 'boost new' to see options\n")
        .contains("  new:type               create a new type\n")
        .doesNotContain("add:reducer");
    assertThat(new TestCli(dir).run("help", "add").stdout()).contains("add:projection").contains("add:reducer");
  }

  @Test
  void reportsUnknownHelpTopics() {
    TestCli.Result result = new TestCli(dir).run("help", "nope");
    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.stderr()).contains("Command nope not found.");
  }

  @Test
  void everyCommandHasAnId() {
    assertThat(Main.COMMANDS).hasSize(18);
    TestProjects.create(dir, CliVersion.VERSION);
    for (String id :
        new String[] {
          "new:project", "new:command", "new:event", "new:entity", "new:read-model", "new:event-handler", "new:query",
          "new:scheduled-command", "new:type", "add:projection", "add:reducer", "build", "clean", "start", "deploy",
          "nuke", "synth", "stub:publish"
        }) {
      assertThat(new TestCli(dir).run(id, "--help").stdout()).as(id).contains("$ boost " + id.split(" ")[0]);
    }
  }
}
