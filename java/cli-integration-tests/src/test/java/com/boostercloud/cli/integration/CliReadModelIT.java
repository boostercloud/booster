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
import com.boostercloud.cli.integration.helper.FileHelper;
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
 * Port of {@code cli.readmodel.integration.ts}: {@code boost new:read-model} without fields (replacing the read model
 * of the test application), with fields and with a projection. TypeScript quotes {@code 'items:Array<Item>'} for
 * the shell; here the arguments are passed to the process as they are. As in TypeScript, no import of {@code Item}
 * is generated (the class does not exist when the read models are generated) and the test adds it; the last test
 * (Java only) creates {@code Item} with {@code boost new:type} and builds the completed sandbox.
 */
@DisplayName("Read model")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliReadModelIT {
  /** TypeScript: {@code // Specify authorized roles here. Use 'all' to authorize anyone}. */
  static final String READ_MODEL_AUTH_PLACEHOLDER =
      "/* Specify authorized roles here. Use All.class to authorize anyone */";
  static final String READ_MODEL_PROJECTION_PLACEHOLDER = "/* NEW CartWithProjectionReadModel HERE */";
  static final String[] EXPECTED_OUTPUT = {"boost new:read-model", "Verifying project", "Creating new read model",
      "Read model generated"};
  static final String ITEM = Sandbox.BASE_PACKAGE + ".common.Item";

  Path readModelSandboxDir;

  @BeforeAll
  void createSandbox() {
    readModelSandboxDir = Sandbox.createSandboxProject("read-model");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(readModelSandboxDir);
  }

  @Test
  @Order(1)
  @DisplayName("valid read model > without fields > should create new read model")
  void withoutFields() {
    Path fileCartReadModel = Sandbox.sourceFile(readModelSandboxDir, "readmodels", "CartReadModel");
    removeFiles(List.of(fileCartReadModel));

    CliResult result = Boost.command(readModelSandboxDir, "new:read-model CartReadModel");
    assertOutputSteps(result, EXPECTED_OUTPUT);

    String expectedEntityContent = loadFixture("readmodels/CartReadModel.java");
    String entityContent = readFileContent(fileCartReadModel);
    assertThat(entityContent).isEqualTo(expectedEntityContent);

    // set Auth
    JavaSources.edit(fileCartReadModel, READ_MODEL_AUTH_PLACEHOLDER, "All.class", JavaSources.ALL);
  }

  @Test
  @Order(2)
  @DisplayName("valid read model > with fields > should create new read model")
  void withFields() {
    Path fileCartWithFieldsReadModel = Sandbox.sourceFile(readModelSandboxDir, "readmodels", "CartWithFieldsReadModel");

    CliResult result =
        Boost.run(readModelSandboxDir, "new:read-model", "CartWithFieldsReadModel", "--fields", "items:Array<Item>");
    assertOutputSteps(result, EXPECTED_OUTPUT);

    String expectedEntityContent = loadFixture("readmodels/CartWithFieldsReadModel.java");
    String entityContent = readFileContent(fileCartWithFieldsReadModel);
    assertThat(entityContent).isEqualTo(expectedEntityContent);

    // set Auth and add the Item import
    JavaSources.edit(fileCartWithFieldsReadModel, READ_MODEL_AUTH_PLACEHOLDER, "All.class", JavaSources.ALL, ITEM);
  }

  @Test
  @Order(3)
  @DisplayName("valid read model > with projection > should create new read model")
  void withProjection() {
    Path fileCartWithProjectionReadModel =
        Sandbox.sourceFile(readModelSandboxDir, "readmodels", "CartWithProjectionReadModel");

    CliResult result =
        Boost.run(
            readModelSandboxDir,
            "new:read-model",
            "CartWithProjectionReadModel",
            "--fields",
            "items:Array<Item>",
            "--projects",
            "Cart:id");
    assertOutputSteps(result, EXPECTED_OUTPUT);

    String expectedEntityContent = loadFixture("readmodels/CartWithProjectionReadModel.java");
    String entityContent = readFileContent(fileCartWithProjectionReadModel);
    assertThat(entityContent).isEqualTo(expectedEntityContent);

    // set Auth, set the projection return (TypeScript: new CartWithProjectionReadModel(entity.id, entity.cartItems))
    // and add the Item import
    JavaSources.edit(fileCartWithProjectionReadModel, READ_MODEL_AUTH_PLACEHOLDER, "All.class", JavaSources.ALL);
    JavaSources.edit(
        fileCartWithProjectionReadModel,
        READ_MODEL_PROJECTION_PLACEHOLDER,
        "ProjectionResult.of(new CartWithProjectionReadModel(entity.id(), entity.cartItems().stream()"
            + ".map(item -> new Item(item.productId(), item.quantity())).toList()))",
        ITEM);
  }

  @Test
  @Order(4)
  @DisplayName("invalid read model > missing read model name > should fail")
  void missingReadModelName() {
    CliResult result = Boost.command(readModelSandboxDir, "new:read-model");

    assertFailsWithMessage(
        result, "You haven't provided a read model name, but it is required, run with --help for usage");
  }

  @Test
  @Order(5)
  @DisplayName("the completed read models compile against the framework (Java)")
  void completedReadModelsCompile() {
    CliResult item = Boost.command(readModelSandboxDir, "new:type Item --fields productId:UUID quantity:number");
    assertThat(item.exitCode()).as(item.describe()).isZero();
    assertThat(FileHelper.readFileContent(Sandbox.sourceFile(readModelSandboxDir, "common", "Item")))
        .contains("UUID productId,");

    CliResult result = Boost.command(readModelSandboxDir, "build");

    assertSucceeds(result, "boost build", "Build complete");
    assertThat(Sandbox.classFile(readModelSandboxDir, "readmodels", "CartReadModel")).exists();
    assertThat(Sandbox.classFile(readModelSandboxDir, "readmodels", "CartWithFieldsReadModel")).exists();
    assertThat(Sandbox.classFile(readModelSandboxDir, "readmodels", "CartWithProjectionReadModel")).exists();
  }
}
