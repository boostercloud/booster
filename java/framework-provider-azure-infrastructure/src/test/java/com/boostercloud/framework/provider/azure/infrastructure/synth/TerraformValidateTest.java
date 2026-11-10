package com.boostercloud.framework.provider.azure.infrastructure.synth;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.boostercloud.framework.provider.azure.infrastructure.ApplicationBuild;
import com.boostercloud.framework.provider.azure.infrastructure.ApplicationBuilder;
import com.boostercloud.framework.provider.azure.infrastructure.Constants;
import com.boostercloud.framework.provider.azure.infrastructure.process.Command;
import com.boostercloud.framework.provider.azure.infrastructure.process.ProcessCommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.support.FakeHttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingCommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingSleeper;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.types.BoosterConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opt-in check (only with {@code BOOSTER_TERRAFORM_VALIDATE=true}) that the synthesized JSON is valid for Terraform
 * and the azurerm provider: {@code terraform init -backend=false} (downloads the provider unless it is in the plugin
 * cache) and {@code terraform validate} in the stack directory. Nothing is planned or applied.
 */
@ExtendWith(TestEnvironment.class)
@EnabledIfEnvironmentVariable(named = "BOOSTER_TERRAFORM_VALIDATE", matches = "true")
class TerraformValidateTest {

  @TempDir Path root;

  @Test
  void the_synthesized_stack_with_streaming_and_subscriptions_validates() {
    validate(Fixtures.streamingConfig());
  }

  @Test
  void the_synthesized_stack_without_subscriptions_validates() {
    validate(Fixtures.noSubscriptionsConfig());
  }

  private void validate(BoosterConfig config) {
    ApplicationBuild build = new ApplicationBuilder(config, null,
        Fixtures.context(root, new RecordingCommandRunner(), new FakeHttpTransport(), new RecordingSleeper()))
        .buildApplication();
    String terraform = Constants.terraformBinary();
    Map<String, String> environment = Map.of("TF_IN_AUTOMATION", "true");
    assertThatCode(() -> {
      ProcessCommandRunner.INSTANCE.run(new Command(build.stackDirectory(),
          List.of(terraform, "init", "-backend=false", "-input=false"), environment, false));
      ProcessCommandRunner.INSTANCE.run(new Command(build.stackDirectory(),
          List.of(terraform, "validate", "-no-color"), environment, false));
    }).doesNotThrowAnyException();
  }
}
