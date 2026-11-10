package com.boostercloud.cli.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.integration.helper.Boost;
import com.boostercloud.cli.integration.helper.CliResult;
import com.boostercloud.cli.integration.helper.FileHelper;
import com.boostercloud.cli.integration.helper.Sandbox;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Java only: the {@code command_not_found} hook forwards unknown commands to the commands the application injects
 * ({@code config.injectable}). The test application injects {@code test} (the only command of the TypeScript test
 * app's {@code src/config/injectable.ts}, which prints {@code Hello, world}) and {@code echo}, which prints its
 * arguments. The CLI sets {@code BOOSTER_CLI_HOOK=true} (the tests remove it from the environment they pass on),
 * removes the first {@code -e <environment>} pair and runs the compiled application without rebuilding it.
 */
@DisplayName("Injected commands (Java)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliHookIT {
  Path hookSandboxDir;

  @BeforeAll
  void createSandbox() {
    hookSandboxDir = Sandbox.createSandboxProject("hook");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(hookSandboxDir);
  }

  @Test
  @Order(1)
  @DisplayName("before the application is compiled > should fail")
  void notCompiled() {
    CliResult result = Boost.command(hookSandboxDir, "test -e local");

    assertThat(result.exitCode()).as(result.describe()).isEqualTo(1);
    assertThat(result.stderr())
        .contains("The application has not been compiled yet (")
        .contains("Run 'boost build' first");
  }

  @Test
  @Order(2)
  @DisplayName("an injected command > is run by the compiled application")
  void injectedCommand() {
    CliResult build = Boost.command(hookSandboxDir, "build");
    assertThat(build.exitCode()).as(build.describe()).isZero();

    CliResult result = Boost.command(hookSandboxDir, "test -e local");

    assertThat(result.exitCode()).as(result.describe()).isZero();
    assertThat(result.stdout()).isEqualTo("Hello, world");
  }

  @Test
  @Order(3)
  @DisplayName("an injected command > receives the arguments without the environment flag")
  void forwardsTheArguments() {
    CliResult result = Boost.command(hookSandboxDir, "echo hello -e local --name=world");

    assertThat(result.exitCode()).as(result.describe()).isZero();
    assertThat(result.stdout()).isEqualTo("echo from cart-demo: hello --name=world");
  }

  @Test
  @Order(4)
  @DisplayName("an injected command > takes the environment from BOOSTER_ENV")
  void environmentVariable() {
    CliResult result = Boost.run(hookSandboxDir, List.of("echo", "from", "variable"), Map.of("BOOSTER_ENV", "local"));

    assertThat(result.exitCode()).as(result.describe()).isZero();
    assertThat(result.stdout()).isEqualTo("echo from cart-demo: from variable");
  }

  @Test
  @Order(5)
  @DisplayName("an unknown injected command > should fail with the application's message")
  void unknownCommand() {
    CliResult result = Boost.command(hookSandboxDir, "nope -e local");

    assertThat(result.exitCode()).as(result.describe()).isEqualTo(1);
    assertThat(result.stderr()).contains("Invalid subcommand for boost - use one of 'test', 'echo'");
  }

  @Test
  @Order(6)
  @DisplayName("only an environment > shows the injected commands")
  void help() {
    CliResult result = Boost.command(hookSandboxDir, "-e local");

    assertThat(result.exitCode()).as(result.describe()).isZero();
    assertThat(result.stdout())
        .contains("USAGE")
        .contains("$ boost <command> [args...]")
        .contains("  - test  prints a greeting")
        .contains("  - echo  prints its arguments and the application name");
  }

  @Test
  @Order(7)
  @DisplayName("outside a Booster project > should fail with command not found")
  void outsideAProject() {
    Path empty = FileHelper.sandboxPathFor("hook-empty");
    FileHelper.removeFolders(List.of(empty));
    FileHelper.createFolder(empty);
    try {
      CliResult result = Boost.command(empty, "test -e local");

      assertThat(result.exitCode()).as(result.describe()).isEqualTo(1);
      assertThat(result.stderr())
          .contains("command test not found. Run 'boost --help' to see the available commands");
    } finally {
      Sandbox.removeSandboxProject(empty);
    }
  }
}
