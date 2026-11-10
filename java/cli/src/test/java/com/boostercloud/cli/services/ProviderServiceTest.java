package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.ProcessExitException;
import com.boostercloud.cli.services.process.RunningProcess;
import com.boostercloud.cli.services.project.ConfiguredEnvironments;
import com.boostercloud.cli.services.project.ProjectConfig;
import com.boostercloud.cli.services.project.ProjectLayout;
import com.boostercloud.cli.testing.FakeProcessService;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/services/provider-service.test.ts}. */
class ProviderServiceTest {
  private static final Random RANDOM = new Random(42);

  private static String alphaNumeric(int length) {
    String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
    }
    return sb.toString();
  }

  @Nested
  class AssertNameIsCorrect {
    @Test
    void shouldThrowAnErrorOnSurpassingProjectNameMaxLength() {
      String input = alphaNumeric(38 + RANDOM.nextInt(20));
      assertThatThrownBy(() -> ProviderService.assertNameIsCorrect(input))
          .isInstanceOf(ProviderService.ForbiddenProjectName.class)
          .hasMessage("Project name cannot be longer than 37 characters:\n\n    Found: '" + input + "'");
    }

    @Test
    void shouldThrowAnErrorIfProjectNameIncludesASpace() {
      assertThatThrownBy(() -> ProviderService.assertNameIsCorrect("lorem ipsum"))
          .hasMessage("Project name cannot contain spaces:\n\n    Found: 'lorem ipsum'");
    }

    @Test
    void shouldThrowAnErrorIfProjectNameIncludesAnUppercaseLetter() {
      String input = alphaNumeric(37).toUpperCase();
      assertThatThrownBy(() -> ProviderService.assertNameIsCorrect(input))
          .hasMessage("Project name cannot contain uppercase letters:\n\n    Found: '" + input + "'");
    }

    @Test
    void shouldThrowAnErrorIfProjectNameIncludesAnUnderscore() {
      assertThatThrownBy(() -> ProviderService.assertNameIsCorrect("test_project_name"))
          .hasMessage("Project name cannot contain underscore:\n\n    Found: 'test_project_name'");
    }

    @Test
    void shouldThrowAnErrorIfProjectNameIsNotAValidArtifactId() {
      assertThatThrownBy(() -> ProviderService.assertNameIsCorrect("my\"app"))
          .hasMessage(
              "Project name cannot contain characters other than lowercase letters, digits, '-' and '.':\n\n    Found:"
                  + " 'my\"app'");
    }

    @Test
    void checksRunInOrder() {
      assertThatThrownBy(() -> ProviderService.assertNameIsCorrect("A B")).hasMessageStartingWith("Project name cannot contain spaces");
    }

    @Test
    void shouldNotThrowAnErrorIfProjectNameIsCorrect() {
      assertThatCode(() -> ProviderService.assertNameIsCorrect(alphaNumeric(37))).doesNotThrowAnyException();
      assertThatCode(() -> ProviderService.assertNameIsCorrect("my-store.v2")).doesNotThrowAnyException();
    }
  }

  @Nested
  class Operations {
    @TempDir Path dir;

    private TestCli cli;
    private ProjectConfig config;

    @BeforeEach
    void setUp() {
      TestProjects.create(dir, "4.1.0");
      cli = new TestCli(dir).env("BOOSTER_ENV", "production").env("JAVA_HOME", "/nonexistent");
      CliContext ctx = cli.context();
      ProjectLayout layout = ProjectLayout.of(dir);
      config =
          new ProjectConfig(
              layout.projectDir(), "production", layout, ConfiguredEnvironments.scan(layout.absoluteSourceDirectory()));
      assertThat(ctx).isNotNull();
    }

    private List<String> expectedCommand(String... operation) {
      List<String> command = new java.util.ArrayList<>();
      command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
      command.add("-cp");
      command.add(dir.resolve("target/classes") + ":" + dir.resolve("target/lib/*"));
      command.add("com.example.mock.Main");
      command.addAll(List.of(operation));
      return command;
    }

    @Test
    void deploysByLaunchingTheApplication() {
      ProviderService.deployToCloudProvider(cli.context(), config);
      FakeProcessService.Call call = cli.process().calls().getFirst();
      assertThat(call.spawned()).isTrue();
      assertThat(call.command()).isEqualTo(expectedCommand("deploy"));
      assertThat(call.cwd()).isEqualTo(dir);
      assertThat(call.environment()).containsEntry("BOOSTER_ENV", "production");
    }

    @Test
    void synthAndNukeLaunchTheirOperations() {
      ProviderService.synthToProvider(cli.context(), config);
      ProviderService.nukeCloudProviderResources(cli.context(), config);
      assertThat(cli.process().calls()).extracting(FakeProcessService.Call::command)
          .containsExactly(expectedCommand("synth"), expectedCommand("nuke"));
    }

    @Test
    void propagatesTheExitCodeOfAFailedOperation() {
      cli.process().spawnExitCode(3);
      assertThatThrownBy(() -> ProviderService.deployToCloudProvider(cli.context(), config))
          .isInstanceOf(ProcessExitException.class)
          .hasMessage("The application finished with exit code 3 while performing the 'deploy' operation")
          .satisfies(e -> assertThat(((ProcessExitException) e).exitCode()).isEqualTo(3));
    }

    @Test
    void validatesTheAppNameBeforeLaunching() {
      ProjectConfig badName =
          new ProjectConfig(dir, "production", config.layout(), new ConfiguredEnvironments(Set.of("production"), false, Map.of("production", "Bad Name")));
      assertThatThrownBy(() -> ProviderService.deployToCloudProvider(cli.context(), badName))
          .hasMessageStartingWith("Project name cannot contain spaces");
      assertThat(cli.process().calls()).isEmpty();
    }

    @Test
    void startsTheServerAndWaitsUntilItListens() {
      AtomicInteger probes = new AtomicInteger();
      cli.portProbe(port -> probes.incrementAndGet() >= 3);
      cli.process().spawnAlive(true);
      RunningProcess process = ProviderService.startProvider(cli.context(), 4000, config);
      assertThat(process.isAlive()).isTrue();
      assertThat(probes).hasValue(3);
      assertThat(cli.process().calls().getFirst().command()).isEqualTo(expectedCommand("start", "--port", "4000"));
    }

    @Test
    void startFailsWhenTheApplicationExitsBeforeListening() {
      cli.portProbe(port -> false);
      cli.process().spawnExitCode(1);
      assertThatThrownBy(() -> ProviderService.startProvider(cli.context(), 3000, config))
          .isInstanceOf(ProcessExitException.class)
          .hasMessage("The application finished with exit code 1 while performing the 'start' operation");
    }

    @Test
    void unsupportedOperations() {
      assertThatThrownBy(() -> ProviderService.supportedInfrastructureMethodOrDie("run", config))
          .hasMessage(
              "Attempted to perform the 'run' operation with a provider that does not support this feature, please"
                  + " check your environment configuration.");
    }

    @Test
    void usesTheJavaOfJavaHomeWhenItExists() {
      TestCli withJavaHome = new TestCli(dir).env("JAVA_HOME", System.getProperty("java.home"));
      assertThat(ProviderService.applicationCommand(withJavaHome.context(), config, "synth", List.of()).getFirst())
          .isEqualTo(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }
  }
}
