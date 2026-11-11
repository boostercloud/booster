package com.boostercloud.cli.integration;

import static com.boostercloud.cli.integration.helper.Expectations.assertFailsWithMessage;
import static com.boostercloud.cli.integration.helper.Expectations.assertOutputSteps;
import static com.boostercloud.cli.integration.helper.Expectations.assertSucceeds;
import static com.boostercloud.cli.integration.helper.FileHelper.loadFixture;
import static com.boostercloud.cli.integration.helper.FileHelper.readFileContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.integration.helper.Boost;
import com.boostercloud.cli.integration.helper.CliResult;
import com.boostercloud.cli.integration.helper.CliTestEnvironment;
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
 * Port of {@code cli.entity.integration.ts}: {@code boost new:entity} without fields, with fields and with a reducer
 * (after {@code boost new:event}), and the missing-name error. The last test (Java only) builds the completed
 * sandbox.
 */
@DisplayName("Entity")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliEntityIT {
  static final String EVENT_ENTITY_ID_PLACEHOLDER = "/* the associated entity ID */";
  static final String ENTITY_REDUCER_PLACEHOLDER = "/* NEW PostWithReducer HERE */";
  static final String[] EXPECTED_OUTPUT = {"boost new:entity", "Verifying project", "Creating new entity",
      "Entity generated"};

  Path entitySandboxDir;

  @BeforeAll
  void createSandbox() {
    entitySandboxDir = Sandbox.createSandboxProject("entity");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(entitySandboxDir);
  }

  @Test
  @Order(1)
  @DisplayName("valid entity > without fields > should create new entity")
  void withoutFields() {
    CliResult result = Boost.command(entitySandboxDir, "new:entity Post");
    assertOutputSteps(result, EXPECTED_OUTPUT);

    // TypeScript reads this fixture with readFileContent instead of loadFixture: same contents
    String expectedEntityContent =
        readFileContent(CliTestEnvironment.fixturesDirectory().resolve("entities/Post.java"));
    String entityContent = readFileContent(Sandbox.sourceFile(entitySandboxDir, "entities", "Post"));
    assertThat(entityContent).isEqualTo(expectedEntityContent);
  }

  @Test
  @Order(2)
  @DisplayName("valid entity > with fields > should create new entity with expected fields")
  void withFields() {
    CliResult result = Boost.command(entitySandboxDir, "new:entity PostWithFields --fields title:string body:string");
    assertOutputSteps(result, EXPECTED_OUTPUT);

    String expectedEntityContent = loadFixture("entities/PostWithFields.java");
    String entityContent = readFileContent(Sandbox.sourceFile(entitySandboxDir, "entities", "PostWithFields"));
    assertThat(entityContent).isEqualTo(expectedEntityContent);
  }

  @Test
  @Order(3)
  @DisplayName("valid entity > with reducer > should create new entity with reducer")
  void withReducer() {
    Path filePostWithReducerEntity = Sandbox.sourceFile(entitySandboxDir, "entities", "PostWithReducer");
    Path filePostCreatedEvent = Sandbox.sourceFile(entitySandboxDir, "events", "PostCreated");

    // Create event
    Boost.command(entitySandboxDir, "new:event PostCreated --fields postId:UUID title:string body:string");

    String expectedEventContent = loadFixture("events/PostCreated.java");
    String eventContent = readFileContent(filePostCreatedEvent);
    assertThat(eventContent).isEqualTo(expectedEventContent);

    // Set event entity ID (TypeScript: this.postId)
    JavaSources.edit(filePostCreatedEvent, EVENT_ENTITY_ID_PLACEHOLDER, "postId");

    // Create entity
    Boost.command(
        entitySandboxDir, "new:entity PostWithReducer --fields title:string body:string --reduces PostCreated");
    String expectedEntityContent = loadFixture("entities/PostWithReducer.java");
    String entityContent = readFileContent(filePostWithReducerEntity);
    assertThat(entityContent).isEqualTo(expectedEntityContent);

    // Set reducer response
    JavaSources.edit(
        filePostWithReducerEntity,
        ENTITY_REDUCER_PLACEHOLDER,
        "new PostWithReducer(event.postId(), event.title(), event.body())");
  }

  @Test
  @Order(4)
  @DisplayName("invalid entity > missing entity name > should fail")
  void missingEntityName() {
    CliResult result = Boost.command(entitySandboxDir, "new:entity");

    assertFailsWithMessage(result, "You haven't provided an entity name, but it is required, run with --help for usage");
  }

  @Test
  @Order(5)
  @DisplayName("the completed entities compile against the framework (Java)")
  void completedEntitiesCompile() {
    CliResult result = Boost.command(entitySandboxDir, "build");

    assertSucceeds(result, "boost build", "Build complete");
    assertThat(Sandbox.classFile(entitySandboxDir, "entities", "Post")).exists();
    assertThat(Sandbox.classFile(entitySandboxDir, "entities", "PostWithFields")).exists();
    assertThat(Sandbox.classFile(entitySandboxDir, "entities", "PostWithReducer")).exists();
    assertThat(Sandbox.classFile(entitySandboxDir, "events", "PostCreated")).exists();
  }
}
