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
 * Port of {@code cli.type.integration.ts}: {@code boost new:type} with and without fields. The last test (Java only)
 * builds the sandbox with the new types.
 */
@DisplayName("Type")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliTypeIT {
  Path typeSandboxDir;

  @BeforeAll
  void createSandbox() {
    typeSandboxDir = Sandbox.createSandboxProject("type");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(typeSandboxDir);
  }

  @Test
  @Order(1)
  @DisplayName("Valid type > should create a new type")
  void shouldCreateANewType() {
    CliResult result = Boost.command(typeSandboxDir, "new:type Item");
    assertOutputSteps(result, "boost new:type", "Verifying project", "Creating new type", "Type generated");

    String expectedTypeContent = loadFixture("common/Item.java");
    String typeContent = readFileContent(Sandbox.sourceFile(typeSandboxDir, "common", "Item"));
    assertThat(typeContent).isEqualTo(expectedTypeContent);
  }

  @Test
  @Order(2)
  @DisplayName("Valid type > with fields > should create a new type with fields")
  void shouldCreateANewTypeWithFields() {
    Boost.command(typeSandboxDir, "new:type ItemWithFields --fields sku:string quantity:number");

    String expectedTypeContent = loadFixture("common/ItemWithFields.java");
    String typeContent = readFileContent(Sandbox.sourceFile(typeSandboxDir, "common", "ItemWithFields"));
    assertThat(typeContent).isEqualTo(expectedTypeContent);
  }

  @Test
  @Order(3)
  @DisplayName("Invalid type > missing type name > should fail")
  void missingTypeName() {
    CliResult result = Boost.command(typeSandboxDir, "new:type");

    assertFailsWithMessage(result, "You haven't provided a type name, but it is required, run with --help for usage");
  }

  @Test
  @Order(4)
  @DisplayName("the types compile against the framework (Java)")
  void typesCompile() {
    CliResult result = Boost.command(typeSandboxDir, "build");

    assertSucceeds(result, "boost build", "Build complete");
    assertThat(Sandbox.classFile(typeSandboxDir, "common", "Item")).exists();
    assertThat(Sandbox.classFile(typeSandboxDir, "common", "ItemWithFields")).exists();
  }
}
