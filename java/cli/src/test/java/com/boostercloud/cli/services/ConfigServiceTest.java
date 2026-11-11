package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.services.project.ConfiguredEnvironments;
import com.boostercloud.cli.services.project.ProjectConfig;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/services/config-service.test.ts}. */
class ConfigServiceTest {
  @TempDir Path dir;

  @BeforeEach
  void createProject() {
    TestProjects.create(dir, "4.1.0");
  }

  @Nested
  class CompileProject {
    @Test
    void cleansAndBuildsWithMaven() {
      TestCli cli = new TestCli(dir);
      ConfigService.compileProject(cli.context(), dir);
      assertThat(cli.process().lines()).containsExactly("mvn -B -q clean", "mvn -B -q package -DskipTests");
      assertThat(cli.process().calls()).allSatisfy(call -> assertThat(call.cwd()).isEqualTo(dir));
    }

    @Test
    void usesTheMavenWrapperWhenTheProjectHasOne() {
      TestProjects.write(dir.resolve("mvnw"), "#!/bin/sh\n");
      TestCli cli = new TestCli(dir);
      ConfigService.compileProject(cli.context(), dir);
      assertThat(cli.process().lines()).containsExactly("./mvnw -B -q clean", "./mvnw -B -q package -DskipTests");
    }

    @Test
    void reportsCompilationErrors() {
      TestCli cli = new TestCli(dir);
      cli.process().failWhen(cmd -> cmd.contains("package"), 1, "[ERROR] COMPILATION ERROR");
      assertThatThrownBy(() -> ConfigService.compileProject(cli.context(), dir))
          .isInstanceOf(CliException.class)
          .hasMessageStartingWith("[Error] Project contains compilation errors:\nCommand failed with exit code 1: mvn -B -q package -DskipTests")
          .hasMessageContaining("[ERROR] COMPILATION ERROR");
    }

    @Test
    void aFailedCleanIsACompilationError() {
      TestCli cli = new TestCli(dir);
      cli.process().failWhen(cmd -> cmd.contains("clean"), 1, "");
      assertThatThrownBy(() -> ConfigService.compileProject(cli.context(), dir))
          .hasMessageStartingWith("[Error] Project contains compilation errors:");
      assertThat(cli.process().lines()).containsExactly("mvn -B -q clean");
    }
  }

  @Nested
  class CleanProject {
    @Test
    void runsMavenClean() {
      TestCli cli = new TestCli(dir);
      ConfigService.cleanProject(cli.context(), dir);
      assertThat(cli.process().lines()).containsExactly("mvn -B -q clean");
    }

    @Test
    void reportsFailures() {
      TestCli cli = new TestCli(dir);
      cli.process().failWhen(cmd -> true, 1, "");
      assertThatThrownBy(() -> ConfigService.cleanProject(cli.context(), dir))
          .hasMessageStartingWith("[Error] Could not clean project:");
    }
  }

  @Nested
  class CompileProjectAndLoadConfig {
    @Test
    void loadsTheConfigWhenTheSelectedEnvironmentExists() {
      TestCli cli = new TestCli(dir).env("BOOSTER_ENV", "local");
      ProjectConfig config = ConfigService.compileProjectAndLoadConfig(cli.context(), dir);
      assertThat(config.environmentName()).isEqualTo("local");
      assertThat(config.appName()).isEqualTo("mock-app");
      assertThat(config.mainClass()).isEqualTo("com.example.mock.Main");
      assertThat(config.classpath())
          .containsExactly(dir.resolve("target/classes").toString(), dir.resolve("target/lib/*").toString());
      assertThat(config.environments().names()).containsExactly("local", "production");
      assertThat(cli.process().lines()).containsExactly("mvn -B -q clean", "mvn -B -q package -DskipTests");
    }

    @Test
    void checksItIsABoosterProjectBeforeBuilding() {
      TestProjects.write(TestProjects.source(dir, "Main"), "class Main {}");
      TestCli cli = new TestCli(dir).env("BOOSTER_ENV", "local");
      assertThatThrownBy(() -> ConfigService.compileProjectAndLoadConfig(cli.context(), dir))
          .hasMessageContaining("The main application file does not start a Booster application");
      assertThat(cli.process().calls()).isEmpty();
    }

    @Test
    void throwsTheRightErrorWhenThereAreNoConfiguredEnvironments() {
      TestProjects.write(TestProjects.source(dir, "config/Config"), "package com.example.mock.config;\nclass Config {}\n");
      TestCli cli = new TestCli(dir).env("BOOSTER_ENV", "local");
      assertThatThrownBy(() -> ConfigService.compileProjectAndLoadConfig(cli.context(), dir))
          .hasMessageStartingWith("You haven't configured any environment");
    }

    @Test
    void throwsTheRightErrorWhenTheEnvironmentDoesNotExist() {
      TestCli cli = new TestCli(dir).env("BOOSTER_ENV", "test");
      assertThatThrownBy(() -> ConfigService.compileProjectAndLoadConfig(cli.context(), dir))
          .hasMessage(
              "The environment 'test' does not match any of the environments you used to configure your Booster"
                  + " project, which are: 'local, production'");
    }
  }

  @Nested
  class CheckEnvironmentWasConfigured {
    @Test
    void requiresAnEnvironment() {
      var environments = new ConfiguredEnvironments(Set.of("local"), false, Map.of());
      assertThatThrownBy(() -> ConfigService.checkEnvironmentWasConfigured(environments, null))
          .hasMessage(
              "You haven't provided any environment. Please make sure you are using option '-e' with a valid"
                  + " environment name");
    }

    @Test
    void skipsTheNameChecksWhenNamesAreNotLiterals() {
      var environments = new ConfiguredEnvironments(Set.of(), true, Map.of());
      assertThatCode(() -> ConfigService.checkEnvironmentWasConfigured(environments, "anything")).doesNotThrowAnyException();
    }

    @Test
    void acceptsAConfiguredEnvironment() {
      var environments = new ConfiguredEnvironments(List.of("local").stream().collect(java.util.stream.Collectors.toSet()), false, Map.of());
      assertThatCode(() -> ConfigService.checkEnvironmentWasConfigured(environments, "local")).doesNotThrowAnyException();
    }
  }
}
