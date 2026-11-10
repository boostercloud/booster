package com.boostercloud.cli.integration;

import static com.boostercloud.cli.integration.helper.Expectations.assertFailsWithMessage;
import static com.boostercloud.cli.integration.helper.Expectations.assertOutputSteps;
import static com.boostercloud.cli.integration.helper.Expectations.assertSucceeds;
import static com.boostercloud.cli.integration.helper.FileHelper.loadFixture;
import static com.boostercloud.cli.integration.helper.FileHelper.readFileContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.integration.helper.Boost;
import com.boostercloud.cli.integration.helper.CliResult;
import com.boostercloud.cli.integration.helper.JavaSources;
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
 * Port of {@code cli.command.integration.ts}: {@code boost new:command}. As in TypeScript the authorization
 * placeholder of each generated command is completed afterwards; the last test (Java only) checks that the completed
 * sandbox builds against the framework.
 */
@DisplayName("Command")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliCommandIT {
  /** TypeScript: {@code // Specify authorized roles here. Use 'all' to authorize anyone}. */
  static final String COMMAND_AUTH_PLACEHOLDER =
      "/* Specify authorized roles here. Use All.class to authorize anyone */";

  Path commandSandboxDir;

  @BeforeAll
  void createSandbox() {
    commandSandboxDir = Sandbox.createSandboxProject("command");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(commandSandboxDir);
  }

  @Test
  @Order(1)
  @DisplayName("Valid command > should create a new command")
  void shouldCreateANewCommand() {
    Path changeCartCommandPath = Sandbox.sourceFile(commandSandboxDir, "commands", "ChangeCart");

    CliResult result = Boost.command(commandSandboxDir, "new:command ChangeCart");
    assertOutputSteps(result, "boost new:command", "Verifying project", "Creating new command", "Command generated");

    String expectedCommandContent = loadFixture("commands/ChangeCart.java");
    String commandContent = readFileContent(changeCartCommandPath);
    assertThat(commandContent).isEqualTo(expectedCommandContent);

    // Set command auth
    JavaSources.edit(changeCartCommandPath, COMMAND_AUTH_PLACEHOLDER, "All.class", JavaSources.ALL);
  }

  @Test
  @Order(2)
  @DisplayName("Valid command > with fields > should create a new command with fields")
  void shouldCreateANewCommandWithFields() {
    Path changeCartWithFieldsCommandPath = Sandbox.sourceFile(commandSandboxDir, "commands", "ChangeCartWithFields");

    Boost.command(commandSandboxDir, "new:command ChangeCartWithFields --fields cartId:UUID sku:string quantity:number");

    String expectedCommandContent = loadFixture("commands/ChangeCartWithFields.java");
    String commandContent = readFileContent(changeCartWithFieldsCommandPath);
    assertThat(commandContent).isEqualTo(expectedCommandContent);

    // Set command auth
    JavaSources.edit(changeCartWithFieldsCommandPath, COMMAND_AUTH_PLACEHOLDER, "All.class", JavaSources.ALL);
  }

  @Test
  @Order(3)
  @DisplayName("Invalid command > missing command name > should fail")
  void missingCommandName() {
    CliResult result = Boost.command(commandSandboxDir, "new:command");

    assertFailsWithMessage(result, "You haven't provided a command name, but it is required, run with --help for usage");
  }

  @Test
  @Order(4)
  @DisplayName("Valid command > an existing command > is not overwritten (Java)")
  void existingCommand() {
    Path changeCartCommandPath = Sandbox.sourceFile(commandSandboxDir, "commands", "ChangeCart");
    String before = readFileContent(changeCartCommandPath);

    CliResult result = Boost.command(commandSandboxDir, "new:command ChangeCart");

    // The question is answered "no" (the input is closed), so the command fails and keeps the file
    assertThat(result.exitCode()).as(result.describe()).isEqualTo(1);
    assertThat(result.stdout()).contains("Resource: \"ChangeCart.java\" already exists. Do you want to overwrite it?");
    assertThat(result.stderr())
        .contains("Error: The 'commands' resource \"ChangeCart.java\" already exists. Please use another resource name");
    assertThat(readFileContent(changeCartCommandPath)).isEqualTo(before);
  }

  @Test
  @Order(5)
  @DisplayName("the completed commands compile against the framework (Java)")
  void completedCommandsCompile() {
    CliResult result = Boost.command(commandSandboxDir, "build");

    assertSucceeds(result, "boost build", "Build complete");
    assertThat(Sandbox.classFile(commandSandboxDir, "commands", "ChangeCart")).exists();
    assertThat(Sandbox.classFile(commandSandboxDir, "commands", "ChangeCartWithFields")).exists();
  }
}
