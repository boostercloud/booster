package com.boostercloud.cli.integration;

import static com.boostercloud.cli.integration.helper.Expectations.assertOutputSteps;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.integration.helper.Boost;
import com.boostercloud.cli.integration.helper.CliResult;
import com.boostercloud.cli.integration.helper.Sandbox;
import java.nio.file.Path;
import java.util.Collections;
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
 * Java only: the cloud operations of the CLI on an environment whose provider cannot perform them. They are run as
 * {@code deploy} and {@code nuke} of {@code cli-helper.ts} run them ({@code --verbose}, {@code nuke --force}). The
 * local provider only supports {@code start}, so the application reports the TypeScript CLI's error
 * ({@code supportedInfrastructureMethodOrDie} of {@code provider-service.ts}) and the CLI exits with a non-zero
 * code. The environment checks the CLI performs before building are pinned too.
 */
@DisplayName("Deploy (Java)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliDeployIT {
  Path deploySandboxDir;

  @BeforeAll
  void createSandbox() {
    deploySandboxDir = Sandbox.createSandboxProject("deploy");
  }

  @AfterAll
  void removeSandbox() {
    Sandbox.removeSandboxProject(deploySandboxDir);
  }

  static String unsupported(String operation) {
    return "Attempted to perform the '"
        + operation
        + "' operation with a provider that does not support this feature, please check your environment"
        + " configuration.";
  }

  @Test
  @Order(3)
  @DisplayName("deploy -e local > fails because the local provider cannot deploy")
  void deployToLocal() {
    CliResult result = Boost.command(deploySandboxDir, "deploy --verbose -e local");

    assertThat(result.exitCode()).as(result.describe()).isEqualTo(1);
    assertOutputSteps(result, "boost deploy [local]", "Deploying");
    assertThat(result.stdout()).doesNotContain("Deployment complete");
    assertThat(result.stderr())
        .contains(unsupported("deploy"))
        .contains("The application finished with exit code 1 while performing the 'deploy' operation");
  }

  @Test
  @Order(4)
  @DisplayName("synth -e local > fails because the local provider cannot synthesize")
  void synthToLocal() {
    CliResult result = Boost.command(deploySandboxDir, "synth --verbose -e local");

    assertThat(result.exitCode()).as(result.describe()).isEqualTo(1);
    assertThat(result.stderr()).contains(unsupported("synth"));
  }

  @Test
  @Order(5)
  @DisplayName("nuke --force -e local > fails because the local provider cannot nuke")
  void nukeLocal() {
    CliResult result = Boost.command(deploySandboxDir, "nuke --verbose -e local --force");

    assertThat(result.exitCode()).as(result.describe()).isEqualTo(1);
    assertThat(result.stderr()).contains(unsupported("nuke"));
  }

  @Test
  @Order(2)
  @DisplayName("deploy -e production > fails before deploying when the environment is not configured")
  void deployToAnUnknownEnvironment() {
    CliResult result = Boost.command(deploySandboxDir, "deploy -e production");

    assertThat(result.exitCode()).as(result.describe()).isEqualTo(1);
    assertThat(result.stderr())
        .contains(
            "The environment 'production' does not match any of the environments you used to configure your Booster"
                + " project, which are: 'local'");
    assertThat(result.stdout()).doesNotContain("Deploying");
  }

  @Test
  @Order(1)
  @DisplayName("deploy without an environment > prints the error and exits with 0, as TypeScript")
  void deployWithoutEnvironment() {
    CliResult result = Boost.run(deploySandboxDir, List.of("deploy"), Collections.singletonMap("BOOSTER_ENV", null));

    // logger.error is oraLogger.fail on the standard output
    assertThat(result.exitCode()).as(result.describe()).isZero();
    assertThat(result.stdout())
        .contains(
            "✖ Error: No environment set. Use the flag `-e` or set the environment variable BOOSTER_ENV to set it"
                + " before running this command. Example usage: `boost deploy -e <environment>`.")
        .doesNotContain("Deploying");
    assertThat(deploySandboxDir.resolve("target")).as("nothing is built").doesNotExist();
  }
}
