package com.boostercloud.cli.integration;

import static com.boostercloud.cli.integration.helper.Expectations.assertFailsWithMessage;
import static com.boostercloud.cli.integration.helper.Expectations.assertOutputSteps;
import static com.boostercloud.cli.integration.helper.Expectations.assertSucceeds;
import static com.boostercloud.cli.integration.helper.FileHelper.loadFixture;
import static com.boostercloud.cli.integration.helper.FileHelper.readFileContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.integration.helper.Boost;
import com.boostercloud.cli.integration.helper.CliResult;
import com.boostercloud.cli.integration.helper.Sandbox;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Port of {@code cli.scheduled-command.integration.ts}: {@code boost new:scheduled-command}. The last test (Java
 * only) builds the sandbox with the new scheduled command.
 */
@DisplayName("Scheduled Command")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliScheduledCommandIT {
  Path scheduledCommandSandboxDir;

  @BeforeAll
  void createSandbox() {
    scheduledCommandSandboxDir = Sandbox.createSandboxProject("scheduled-command");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(scheduledCommandSandboxDir);
  }

  @Test
  @Order(1)
  @DisplayName("Valid scheduled command > should create a new scheduled command")
  void shouldCreateANewScheduledCommand() {
    CliResult result = Boost.command(scheduledCommandSandboxDir, "new:scheduled-command CheckCart");
    assertOutputSteps(
        result,
        "boost new:scheduled-command",
        "Verifying project",
        "Creating new scheduled command",
        "Scheduled command generated");

    String expectedCommandContent = loadFixture("scheduledcommands/CheckCart.java");
    String commandContent =
        readFileContent(Sandbox.sourceFile(scheduledCommandSandboxDir, "scheduledcommands", "CheckCart"));
    assertThat(commandContent).isEqualTo(expectedCommandContent);
  }

  @Test
  @Order(2)
  @DisplayName("Invalid scheduled command > missing scheduled command name > should fail")
  void missingScheduledCommandName() {
    CliResult result = Boost.command(scheduledCommandSandboxDir, "new:scheduled-command");

    assertFailsWithMessage(
        result, "You haven't provided a scheduled command name, but it is required, run with --help for usage");
  }

  @Test
  @Order(3)
  @DisplayName("the scheduled command compiles against the framework (Java)")
  void scheduledCommandCompiles() {
    CliResult result = Boost.command(scheduledCommandSandboxDir, "build");

    assertSucceeds(result, "boost build", "Build complete");
    assertThat(Sandbox.classFile(scheduledCommandSandboxDir, "scheduledcommands", "CheckCart")).exists();
  }
}
