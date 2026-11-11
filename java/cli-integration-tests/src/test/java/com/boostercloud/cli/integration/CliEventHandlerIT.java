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
 * Port of {@code cli.event-handler.integration.ts}: {@code boost new:event-handler} for the {@code CartItemChanged}
 * event of the test application, and the errors when the name or the event is missing. The last test (Java only)
 * builds the sandbox with the new handler.
 */
@DisplayName("Event handler")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliEventHandlerIT {
  static final String MISSING_NAME =
      "You haven't provided an event handler name, but it is required, run with --help for usage";

  Path eventHandlerSandboxDir;

  @BeforeAll
  void createSandbox() {
    eventHandlerSandboxDir = Sandbox.createSandboxProject("event-handler");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(eventHandlerSandboxDir);
  }

  @Test
  @Order(1)
  @DisplayName("Valid event handler > should create new event handler")
  void shouldCreateNewEventHandler() {
    CliResult result = Boost.command(eventHandlerSandboxDir, "new:event-handler HandleCartChange -e CartItemChanged");
    assertOutputSteps(
        result, "boost new:event-handler", "Verifying project", "Creating new event handler", "Event handler generated");

    String expectedEventContent = loadFixture("eventhandlers/HandleCartChange.java");
    String eventContent =
        readFileContent(Sandbox.sourceFile(eventHandlerSandboxDir, "eventhandlers", "HandleCartChange"));

    assertThat(eventContent).isEqualTo(expectedEventContent);
  }

  @Test
  @Order(2)
  @DisplayName("Invalid event handler > without name and event > should fail")
  void withoutNameAndEvent() {
    CliResult result = Boost.command(eventHandlerSandboxDir, "new:event-handler");

    assertFailsWithMessage(result, MISSING_NAME);
  }

  @Test
  @Order(3)
  @DisplayName("Invalid event handler > Without name > should fail")
  void withoutName() {
    CliResult result = Boost.command(eventHandlerSandboxDir, "new:event-handler -e CartPaid");

    assertFailsWithMessage(result, MISSING_NAME);
  }

  @Test
  @Order(4)
  @DisplayName("Invalid event handler > Without event > should fail")
  void withoutEvent() {
    CliResult result = Boost.command(eventHandlerSandboxDir, "new:event-handler CartPaid");

    assertFailsWithMessage(result, "You haven't provided an event, but it is required, run with --help for usage");
    assertThat(Sandbox.sourceFile(eventHandlerSandboxDir, "eventhandlers", "CartPaid")).doesNotExist();
  }

  @Test
  @Order(5)
  @DisplayName("the event handler compiles against the framework (Java)")
  void eventHandlerCompiles() {
    CliResult result = Boost.command(eventHandlerSandboxDir, "build");

    assertSucceeds(result, "boost build", "Build complete");
    assertThat(Sandbox.classFile(eventHandlerSandboxDir, "eventhandlers", "HandleCartChange")).exists();
  }
}
