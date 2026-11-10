package com.boostercloud.cli.integration;

import static com.boostercloud.cli.integration.helper.Expectations.assertSucceeds;
import static com.boostercloud.cli.integration.helper.FileHelper.fileExists;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.integration.helper.Boost;
import com.boostercloud.cli.integration.helper.CliResult;
import com.boostercloud.cli.integration.helper.Sandbox;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Port of {@code cli.clean.integration.ts}: {@code boost clean} removes what {@code boost build} produced
 * ({@code dist} in TypeScript, {@code target} here).
 */
@DisplayName("Clean")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CliCleanIT {
  Path cleanSandboxDir;

  @BeforeAll
  void createSandbox() {
    cleanSandboxDir = Sandbox.createSandboxProject("clean");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(cleanSandboxDir);
  }

  @Test
  @DisplayName("Valid clean > should clean the project after build")
  void shouldCleanTheProjectAfterBuild() {
    CliResult build = Boost.command(cleanSandboxDir, "build");
    assertThat(build.exitCode()).as(build.describe()).isZero();

    assertThat(fileExists(cleanSandboxDir.resolve("target"))).isTrue();

    CliResult result = Boost.command(cleanSandboxDir, "clean");

    assertSucceeds(result, "boost clean", "Checking project structure", "Cleaning project", "Clean complete");
    assertThat(fileExists(cleanSandboxDir.resolve("target"))).isFalse();
  }
}
