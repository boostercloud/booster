package com.boostercloud.cli.integration;

import static com.boostercloud.cli.integration.helper.Expectations.assertFailsWithMessage;
import static com.boostercloud.cli.integration.helper.Expectations.assertOutputSteps;
import static com.boostercloud.cli.integration.helper.Expectations.assertSucceeds;
import static com.boostercloud.cli.integration.helper.FileHelper.loadFixture;
import static com.boostercloud.cli.integration.helper.FileHelper.readFileContent;
import static com.boostercloud.cli.integration.helper.FileHelper.removeFiles;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.integration.helper.Boost;
import com.boostercloud.cli.integration.helper.CliResult;
import com.boostercloud.cli.integration.helper.JavaSources;
import com.boostercloud.cli.integration.helper.Sandbox;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Port of {@code cli.event.integration.ts}: {@code boost new:event} with and without fields. As in TypeScript the
 * second test deletes the file created by the first one (so the tests depend on their order). The last test (Java
 * only) builds the completed sandbox.
 */
@DisplayName("Event")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliEventIT {
  static final String EVENT_ENTITY_ID_PLACEHOLDER = "/* the associated entity ID */";

  Path eventSandboxDir;

  @BeforeAll
  void createSandbox() {
    eventSandboxDir = Sandbox.createSandboxProject("event");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(eventSandboxDir);
  }

  @Test
  @Order(1)
  @DisplayName("Valid event > should create new event")
  void shouldCreateNewEvent() {
    CliResult result = Boost.command(eventSandboxDir, "new:event CartChanged");

    assertOutputSteps(result, "boost new:event", "Verifying project", "Creating new event", "Event generated");
  }

  @Test
  @Order(2)
  @DisplayName("Valid event > without fields > should create new event")
  void withoutFields() {
    Path fileCartChangedEvent = Sandbox.sourceFile(eventSandboxDir, "events", "CartChanged");
    removeFiles(List.of(fileCartChangedEvent));

    Boost.command(eventSandboxDir, "new:event CartChanged");

    String expectedEventContent = loadFixture("events/CartChanged.java");
    String eventContent = readFileContent(fileCartChangedEvent);
    assertThat(eventContent).isEqualTo(expectedEventContent);

    // Set event entity ID (TypeScript: 'some-id')
    JavaSources.edit(fileCartChangedEvent, EVENT_ENTITY_ID_PLACEHOLDER, "UUID.of(\"some-id\")");
  }

  @Test
  @Order(3)
  @DisplayName("Valid event > with fields > should create new event")
  void withFields() {
    Path fileCartChangedWithFieldsEvent = Sandbox.sourceFile(eventSandboxDir, "events", "CartChangedWithFields");

    Boost.command(eventSandboxDir, "new:event CartChangedWithFields --fields cartId:UUID sku:string quantity:number");

    String expectedEventContent = loadFixture("events/CartChangedWithFields.java");
    String eventContent = readFileContent(fileCartChangedWithFieldsEvent);
    assertThat(eventContent).isEqualTo(expectedEventContent);

    // Set event entity ID (TypeScript: this.cartId)
    JavaSources.edit(fileCartChangedWithFieldsEvent, EVENT_ENTITY_ID_PLACEHOLDER, "cartId");
  }

  @Test
  @Order(4)
  @DisplayName("Invalid event > missing event name > should fail")
  void missingEventName() {
    CliResult result = Boost.command(eventSandboxDir, "new:event");

    assertFailsWithMessage(result, "You haven't provided an event name, but it is required, run with --help for usage");
  }

  @Test
  @Order(5)
  @DisplayName("the completed events compile against the framework (Java)")
  void completedEventsCompile() {
    CliResult result = Boost.command(eventSandboxDir, "build");

    assertSucceeds(result, "boost build", "Build complete");
    assertThat(Sandbox.classFile(eventSandboxDir, "events", "CartChanged")).exists();
    assertThat(Sandbox.classFile(eventSandboxDir, "events", "CartChangedWithFields")).exists();
  }
}
