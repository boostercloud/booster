package com.boostercloud.integration.azurecontract;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.infrastructure.Constants;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.integration.harness.E2eSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The Azure contract of the application, part 4 (opt-in, {@code BOOSTER_TERRAFORM_VALIDATE=true}): Terraform and the
 * azurerm provider accept the stack synthesized for the application. The application synthesizes as the CLI launches
 * it ({@link AzureSynthesisContractIT}), then {@code terraform init -backend=false} (downloads the azurerm 5.x provider
 * unless it is in the plugin cache, e.g. {@code TF_PLUGIN_CACHE_DIR}) and {@code terraform validate} run in the stack
 * directory. Nothing is planned or applied. The binary is {@code BOOSTER_TERRAFORM_BINARY} (default
 * {@code terraform}, e.g. {@code tofu}).
 */
@EnabledIfEnvironmentVariable(named = "BOOSTER_TERRAFORM_VALIDATE", matches = "true")
@DisplayName("Azure Terraform validation (opt-in)")
class AzureTerraformValidateIT {

  private CliBuiltProject project;
  private Path stackDirectory;

  @BeforeAll
  void synthesize() {
    project = CliBuiltProject.create("azure-validate", TypeScriptAzureApp.ASSETS);
    CliBuiltProject.Launch synth = project.run("synth", AzureSynthesisContractIT.SYNTH_ENVIRONMENT,
        AzureSynthesisContractIT.UNSET_VARIABLES, Duration.ofMinutes(3));
    assertThat(synth.exitCode()).as("exit code of synth, output in %s:%n%s", synth.logFile(), synth.output()).isZero();
    stackDirectory = project.directory().resolve("cdktf.out").resolve("stacks").resolve(TypeScriptAzureApp.STACK);
  }

  @AfterAll
  void removeProject() {
    if (project != null) {
      project.close();
    }
  }

  @Test
  @DisplayName("terraform validate accepts the synthesized stack of the application")
  void validates() {
    String terraform = Constants.terraformBinary();
    Result init = run(List.of(terraform, "init", "-backend=false", "-input=false", "-no-color"), Duration.ofMinutes(10));
    assertThat(init.exitCode()).as("terraform init:%n%s", init.output()).isZero();

    Result validate = run(List.of(terraform, "validate", "-no-color", "-json"), Duration.ofMinutes(5));

    Map<String, Object> report = BoosterJson.parseObject(validate.output());
    assertThat(report.get("valid")).as("terraform validate:%n%s", validate.output()).isEqualTo(true);
    assertThat(report.get("error_count")).isEqualTo(0);
    assertThat(validate.exitCode()).isZero();
  }

  private record Result(int exitCode, String output) {}

  private Result run(List<String> command, Duration timeout) {
    Path log = E2eSettings.logsDirectory().resolve("azure-validate-" + command.get(1) + ".log");
    Process process = null;
    try {
      Files.createDirectories(log.getParent());
      ProcessBuilder builder = new ProcessBuilder(command).directory(stackDirectory.toFile())
          .redirectErrorStream(true).redirectOutput(log.toFile());
      builder.environment().put("TF_IN_AUTOMATION", "true");
      process = builder.start();
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException(String.join(" ", command) + " did not end within " + timeout + "; see " + log);
      }
      return new Result(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not run " + String.join(" ", command), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }
}
