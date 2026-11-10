package com.boostercloud.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.services.Prompter;
import com.boostercloud.cli.services.process.RunningProcess;
import com.boostercloud.cli.services.project.ConfiguredEnvironments;
import com.boostercloud.cli.services.project.ProjectConfig;
import com.boostercloud.cli.services.project.ProjectLayout;
import com.boostercloud.cli.testing.FakeProcessService;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Ports of {@code test/commands/{build,clean,deploy,start,nuke}.test.ts} (and {@code synth}, which has no TypeScript
 * test). Maven and the application are replaced by the fake process runner.
 */
class ProjectCommandsTest {
  @TempDir Path dir;

  private TestCli cli;

  @BeforeEach
  void setUp() {
    TestProjects.create(dir, "4.1.0");
    cli = new TestCli(dir);
  }

  private List<String> mavenCalls() {
    return cli.process().calls().stream().filter(c -> !c.spawned()).map(FakeProcessService.Call::line).toList();
  }

  private List<FakeProcessService.Call> spawned() {
    return cli.process().calls().stream().filter(FakeProcessService.Call::spawned).toList();
  }

  private ProjectConfig config() {
    return config("test-env");
  }

  private ProjectConfig config(String environment) {
    ProjectLayout layout = ProjectLayout.of(dir);
    return new ProjectConfig(dir, environment, layout, ConfiguredEnvironments.scan(layout.absoluteSourceDirectory()));
  }

  @Nested
  class BuildCommand {
    @Test
    void initCallsCheckCurrentDirBoosterVersion() {
      assertThatCode(() -> new Build(cli.context()).init()).doesNotThrowAnyException();
    }

    @Test
    void cleansAndBuildsTheProject() {
      TestCli.Result result = cli.run("build");
      assertThat(result.exitCode()).isZero();
      assertThat(mavenCalls()).containsExactly("mvn -B -q clean", "mvn -B -q package -DskipTests");
      assertThat(result.stdout())
          .isEqualTo(
              """
              ℹ boost build 🚀
              - Checking project structure
              ✔ Checking project structure
              - Building project
              ✔ Building project
              ℹ Build complete!
              """);
    }

    @Test
    void reportsCompilationErrorsShortlyAndFullyWithVerbose() {
      cli.process().failWhen(cmd -> cmd.contains("package"), 1, "[ERROR] Main.java:[3,1] class, interface expected");
      TestCli.Result result = cli.run("build");
      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.stdout()).contains("✖ Building project");
      assertThat(result.stderr())
          .contains("Error: [Error] Project contains compilation errors:")
          .contains("(You can see the full error logs in ./errors.log)")
          .doesNotContain("class, interface expected");
      assertThat(TestProjects.read(dir.resolve("errors.log")))
          .contains("] boost build ")
          .contains("class, interface expected");

      TestCli.Result verbose = cli.run("build", "--verbose");
      assertThat(verbose.stderr()).contains("class, interface expected").contains("(You can see the full error logs in ./errors.log)");
    }

    @Test
    void failsOutsideABoosterProject() {
      TestCli outside = new TestCli(dir.resolve("src"));
      TestCli.Result result = outside.run("build");
      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.stderr()).contains("There was an error when recognizing the application");
      assertThat(outside.process().calls()).isEmpty();
    }

    @Test
    void rejectsUnknownFlags() {
      TestCli.Result result = cli.run("build", "--nonexistingoption");
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains("Nonexistent flag: --nonexistingoption");
    }
  }

  @Nested
  class CleanCommand {
    @Test
    void cleansTheProject() {
      TestCli.Result result = cli.run("clean");
      assertThat(result.exitCode()).isZero();
      assertThat(mavenCalls()).containsExactly("mvn -B -q clean");
      assertThat(result.stdout()).contains("✔ Cleaning project").contains("ℹ Clean complete!");
    }

    @Test
    void reportsFailures() {
      cli.process().failWhen(cmd -> true, 1, "");
      TestCli.Result result = cli.run("clean");
      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.stderr()).contains("[Error] Could not clean project:");
    }
  }

  @Nested
  class DeployCommand {
    @Test
    void initCallsCheckCurrentDirBoosterVersion() {
      assertThatCode(() -> new Deploy(cli.context()).init()).doesNotThrowAnyException();
    }

    @Test
    void withoutEnvironmentShowsTheError() {
      TestCli.Result result = cli.run("deploy");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).contains("✖ Error: No environment set");
      assertThat(cli.process().calls()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-e", "--environment"})
    void withIncompleteEnvironmentFlag(String flag) {
      TestCli.Result result = cli.run("deploy", flag);
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains("--environment expects a value");
      assertThat(cli.process().calls()).isEmpty();
    }

    @Test
    void deploysWithACorrectEnvironment() {
      TestCli.Result result = cli.run("deploy", "-e", "production");
      assertThat(result.exitCode()).isZero();
      assertThat(mavenCalls()).containsExactly("mvn -B -q clean", "mvn -B -q package -DskipTests");
      assertThat(spawned()).singleElement().satisfies(call -> {
        assertThat(call.command()).endsWith("com.example.mock.Main", "deploy");
        assertThat(call.environment()).containsEntry("BOOSTER_ENV", "production");
      });
      assertThat(result.stdout())
          .isEqualTo(
              """
              ℹ boost deploy [production] 🚀
              - Deploying
              ✔ Deploying
              ℹ Deployment complete!
              """);
    }

    @Test
    void usesTheEnvironmentVariable() {
      cli.env("BOOSTER_ENV", "local");
      assertThat(cli.run("deploy").exitCode()).isZero();
      assertThat(spawned()).singleElement().satisfies(call -> assertThat(call.environment()).containsEntry("BOOSTER_ENV", "local"));
    }

    @Test
    void withANonexistingFlag() {
      TestCli.Result result = cli.run("deploy", "-e", "production", "--nonexistingoption");
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains("Nonexistent flag: --nonexistingoption");
      assertThat(result.stdout()).doesNotContain("Deployment complete!");
      assertThat(cli.process().calls()).isEmpty();
    }

    @Test
    void withAnUnknownEnvironment() {
      TestCli.Result result = cli.run("deploy", "-e", "staging");
      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.stderr()).contains("The environment 'staging' does not match any of the environments");
      assertThat(spawned()).isEmpty();
    }

    @Test
    void propagatesTheExitCodeOfTheApplication() {
      cli.process().spawnExitCode(42);
      TestCli.Result result = cli.run("deploy", "-e", "production");
      assertThat(result.exitCode()).isEqualTo(42);
      assertThat(result.stdout()).contains("✖ Deploying").doesNotContain("Deployment complete!");
      assertThat(result.stderr()).contains("The application finished with exit code 42 while performing the 'deploy' operation");
    }

    @Test
    void runTasksFailsGracefullyWhenTheLoaderFails() {
      cli.env("BOOSTER_ENV", "test-env");
      List<String> deployed = new ArrayList<>();
      assertThatThrownBy(
              () ->
                  Deploy.runTasks(
                      cli.context(),
                      () -> {
                        throw new CliException("weird exception");
                      },
                      c -> deployed.add("deployed")))
          .hasMessageContaining("weird exception");
      assertThat(deployed).isEmpty();
    }

    @Test
    void runTasksStartsDeployment() {
      cli.env("BOOSTER_ENV", "test-env");
      List<String> deployed = new ArrayList<>();
      ProjectConfig config = config();
      Deploy.runTasks(cli.context(), () -> config, c -> deployed.add(c.environmentName()));
      assertThat(cli.stdout()).contains("Deployment complete");
      assertThat(deployed).containsExactly("test-env");
    }
  }

  @Nested
  class SynthCommand {
    @Test
    void synthesizesWithACorrectEnvironment() {
      TestCli.Result result = cli.run("synth", "-e", "production");
      assertThat(result.exitCode()).isZero();
      assertThat(spawned()).singleElement().satisfies(call -> assertThat(call.command()).endsWith("synth"));
      assertThat(result.stdout()).contains("ℹ boost synth [production] 🚀").contains("✔ Synth").contains("ℹ Synth complete!");
    }

    @Test
    void withoutEnvironmentShowsTheError() {
      TestCli.Result result = cli.run("synth");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).contains("No environment set");
    }
  }

  @Nested
  class StartCommand {
    @Test
    void initCallsCheckCurrentDirBoosterVersion() {
      assertThatCode(() -> new Start(cli.context()).init()).doesNotThrowAnyException();
    }

    @Test
    void withoutEnvironmentShowsTheError() {
      TestCli.Result result = cli.run("start");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).contains("✖ Error: No environment set");
      assertThat(cli.process().calls()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-e", "--environment"})
    void withIncompleteEnvironmentFlag(String flag) {
      TestCli.Result result = cli.run("start", flag);
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains("--environment expects a value");
    }

    @Test
    void startsOnTheDefaultPort() {
      TestCli.Result result = cli.run("start", "-e", "local");
      assertThat(result.exitCode()).isZero();
      assertThat(spawned()).singleElement().satisfies(call -> {
        assertThat(call.command()).endsWith("com.example.mock.Main", "start", "--port", "3000");
        assertThat(call.environment()).containsEntry("BOOSTER_ENV", "local").containsEntry("BOOSTER_INTERNAL_LOCAL_PORT", "3000");
      });
      assertThat(result.stdout())
          .isEqualTo(
              """
              ℹ boost debug [local] 🐛
              - Starting debug server on port 3000
              ✔ Starting debug server on port 3000
              """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"--port", "-p"})
    void startsOnTheGivenPort(String flag) {
      TestCli.Result result = cli.run("start", "-e", "local", flag, "5000");
      assertThat(result.stdout()).contains("Starting debug server on port 5000");
      assertThat(spawned().getFirst().command()).endsWith("--port", "5000");
      assertThat(spawned().getFirst().environment()).containsEntry("BOOSTER_INTERNAL_LOCAL_PORT", "5000");
    }

    @Test
    void waitsForTheServerAndReturnsItsExitCode() {
      AtomicInteger probes = new AtomicInteger();
      cli.portProbe(port -> probes.incrementAndGet() > 2);
      cli.process().spawnAlive(true).spawnExitCode(130);
      TestCli.Result result = cli.run("start", "-e", "local");
      assertThat(result.exitCode()).isEqualTo(130);
      assertThat(probes).hasValue(3);
      assertThat(result.stdout()).contains("✔ Starting debug server on port 3000");
    }

    @Test
    void withANonexistingFlag() {
      TestCli.Result result = cli.run("start", "-e", "local", "--nonexistingoption");
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains("Nonexistent flag: --nonexistingoption");
      assertThat(result.stdout()).doesNotContain("Starting debug server on port");
    }

    @ParameterizedTest
    @ValueSource(strings = {"--port", "-p"})
    void withIncompletePort(String flag) {
      TestCli.Result result = cli.run("start", "-e", "local", flag);
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains("--port expects a value");
      assertThat(cli.process().calls()).isEmpty();
    }

    @Test
    void withANonIntegerPort() {
      TestCli.Result result = cli.run("start", "-e", "local", "-p", "abc");
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains("Expected an integer but received: abc");
    }

    @Test
    void withoutDefiningEnvironmentAndPort() {
      TestCli.Result result = cli.run("start", "-p", "5000");
      assertThat(result.stdout()).contains("No environment set");
      assertThat(cli.process().calls()).isEmpty();
    }

    @Test
    void runTasksCallsTheRunner() {
      cli.env("BOOSTER_ENV", "test-env");
      List<Integer> ports = new ArrayList<>();
      ProjectConfig config = config();
      RunningProcess process =
          Start.runTasks(
              cli.context(),
              3000,
              () -> config,
              c -> {
                ports.add(3000);
                return cli.process().spawn(List.of("app"), dir);
              });
      assertThat(ports).containsExactly(3000);
      assertThat(process).isNotNull();
    }
  }

  @Nested
  class NukeCommand {
    private Prompter prompter(String answer) {
      return new Prompter(new BufferedReader(new StringReader(answer)), new PrintStream(OutputStream.nullOutputStream()), null);
    }

    @Test
    void initCallsCheckCurrentDirBoosterVersion() {
      assertThatCode(() -> new Nuke(cli.context()).init()).doesNotThrowAnyException();
    }

    @Test
    void withoutEnvironmentShowsTheError() {
      TestCli.Result result = cli.run("nuke");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).contains("✖ Error: No environment set");
      assertThat(cli.process().calls()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-e", "--environment"})
    void withIncompleteEnvironmentFlag(String flag) {
      TestCli.Result result = cli.run("nuke", flag);
      assertThat(result.stderr()).contains("--environment expects a value");
      assertThat(cli.process().calls()).isEmpty();
    }

    @Test
    void nukesWhenTheUserTypesTheApplicationName() {
      TestCli.Result result = cli.stdin("mock-app\n").run("nuke", "-e", "production");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout())
          .contains("? Please, enter the app name to confirm deletion of all resources: ")
          .contains("ℹ boost nuke [production] 🧨")
          .contains("✔ Removing")
          .contains("ℹ Removal complete!");
      assertThat(mavenCalls()).containsExactly("mvn -B -q clean", "mvn -B -q package -DskipTests");
      assertThat(spawned()).singleElement().satisfies(call -> assertThat(call.command()).endsWith("nuke"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--force", "-f"})
    void nukesWithoutAskingWithForce(String flag) {
      TestCli.Result result = cli.run("nuke", "-e", "production", flag);
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).doesNotContain("Please, enter the app name").contains("Removal complete!");
      assertThat(spawned()).hasSize(1);
    }

    @Test
    void stopsWithAWrongApplicationName() {
      TestCli.Result result = cli.stdin("fake app 2\n").run("nuke", "-e", "production");
      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.stderr()).contains("Wrong app name, stopping nuke!");
      assertThat(result.stdout()).doesNotContain("Removal complete!");
      assertThat(mavenCalls()).isNotEmpty();
      assertThat(spawned()).isEmpty();
    }

    @Test
    void withANonexistingFlag() {
      TestCli.Result result = cli.run("nuke", "-e", "production", "--nonexistingoption");
      assertThat(result.stderr()).contains("Nonexistent flag: --nonexistingoption");
      assertThat(spawned()).isEmpty();
    }

    @Test
    void withoutDefiningEnvironmentAndForce() {
      TestCli.Result result = cli.run("nuke", "--force");
      assertThat(result.stdout()).contains("No environment set");
      assertThat(spawned()).isEmpty();
    }

    @Test
    void askToConfirmRemovalComparesTheAppName() throws Exception {
      ProjectConfig config = config("production");
      assertThat(Nuke.askToConfirmRemoval(prompter("mock-app\n"), false, () -> config).call()).isSameAs(config);
      assertThatThrownBy(() -> Nuke.askToConfirmRemoval(prompter("fake app 2\n"), false, () -> config).call())
          .hasMessage("Wrong app name, stopping nuke!");
      assertThat(Nuke.askToConfirmRemoval(prompter("anything\n"), true, () -> config).call()).isSameAs(config);
      // without a literal appName for the environment, the Maven artifact id is expected
      ProjectConfig unknownAppName = config("staging");
      assertThat(Nuke.askToConfirmRemoval(prompter("mock-project\n"), false, () -> unknownAppName).call())
          .isSameAs(unknownAppName);
    }

    @Test
    void runTasksFailsGracefully() {
      cli.env("BOOSTER_ENV", "test-env");
      List<String> nuked = new ArrayList<>();
      ProjectConfig config = config("production");
      assertThatThrownBy(
              () -> Nuke.runTasks(cli.context(), Nuke.askToConfirmRemoval(prompter("other\n"), false, () -> config), c -> nuked.add("x")))
          .hasMessageContaining("Wrong app name, stopping nuke!");
      assertThat(nuked).isEmpty();
      Nuke.runTasks(cli.context(), Nuke.askToConfirmRemoval(prompter("mock-app\n"), false, () -> config), c -> nuked.add("x"));
      assertThat(nuked).containsExactly("x");
      assertThat(cli.stdout()).contains("Removal complete!");
    }
  }
}
