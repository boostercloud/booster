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
 * {@code boost new:query}, which the TypeScript CLI has but its integration suite does not test; written like the
 * other generator suites (the generated file, the missing-name error, the completed query compiles).
 */
@DisplayName("Query (Java)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliQueryIT {
  static final String QUERY_AUTH_PLACEHOLDER = "/* Specify authorized roles here. Use All.class to authorize anyone */";
  static final String QUERY_CODE_PLACEHOLDER = "/* YOUR CODE HERE */";

  Path querySandboxDir;

  @BeforeAll
  void createSandbox() {
    querySandboxDir = Sandbox.createSandboxProject("query");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(querySandboxDir);
  }

  @Test
  @Order(1)
  @DisplayName("Valid query > with fields > should create a new query")
  void shouldCreateANewQuery() {
    Path cartTotalQueryPath = Sandbox.sourceFile(querySandboxDir, "queries", "CartTotal");

    CliResult result = Boost.command(querySandboxDir, "new:query CartTotal --fields cartId:UUID");
    assertOutputSteps(result, "boost new:query", "Verifying project", "Creating new query", "Query generated");

    assertThat(readFileContent(cartTotalQueryPath)).isEqualTo(loadFixture("queries/CartTotal.java"));

    JavaSources.edit(cartTotalQueryPath, QUERY_AUTH_PLACEHOLDER, "All.class", JavaSources.ALL);
    JavaSources.edit(cartTotalQueryPath, QUERY_CODE_PLACEHOLDER, "return query.cartId().toString();");
  }

  @Test
  @Order(2)
  @DisplayName("Invalid query > missing query name > should fail")
  void missingQueryName() {
    CliResult result = Boost.command(querySandboxDir, "new:query");

    assertFailsWithMessage(result, "You haven't provided a query name, but it is required, run with --help for usage");
  }

  @Test
  @Order(3)
  @DisplayName("the completed query compiles against the framework")
  void completedQueryCompiles() {
    CliResult result = Boost.command(querySandboxDir, "build");

    assertSucceeds(result, "boost build", "Build complete");
    assertThat(Sandbox.classFile(querySandboxDir, "queries", "CartTotal")).exists();
  }
}
