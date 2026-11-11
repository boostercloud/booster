package com.boostercloud.framework.provider.azure.infrastructure.synth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.boostercloud.framework.provider.azure.infrastructure.ApplicationBuild;
import com.boostercloud.framework.provider.azure.infrastructure.ApplicationBuilder;
import com.boostercloud.framework.provider.azure.infrastructure.Constants;
import com.boostercloud.framework.provider.azure.infrastructure.process.Command;
import com.boostercloud.framework.provider.azure.infrastructure.process.ProcessCommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.support.FakeHttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingCommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingSleeper;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestRockets;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Opt-in check (only with {@code BOOSTER_TERRAFORM_VALIDATE=true}) that the synthesized JSON is valid for Terraform
 * and the azurerm provider: {@code terraform init -backend=false} (downloads the provider unless it is in the plugin
 * cache) and {@code terraform validate} in the stack directory. Nothing is planned or applied.
 *
 * <p>Three stacks are synthesized here (streaming with subscriptions, no subscriptions, and rockets with WAF and the
 * basic plan, whose webhook-like and files-like test rockets replace the gateway's {@code backend_address_pool},
 * {@code url_path_map.0.path_rule} and {@code rewrite_rule_set.0.rewrite_rule}). The real rockets' stacks are
 * validated through their golden files ({@code rockets/*}{@code /src/test/resources/golden/*.cdk.tf.json}, which the
 * rocket modules' golden tests pin to their synthesis), together with this module's golden files.
 */
@ExtendWith(TestEnvironment.class)
@EnabledIfEnvironmentVariable(named = "BOOSTER_TERRAFORM_VALIDATE", matches = "true")
class TerraformValidateTest {

  private static final Path GOLDEN = Path.of("src/test/resources/golden");
  private static final Path ROCKETS = Path.of("..", "rockets");

  @TempDir Path root;

  @Test
  void the_synthesized_stack_with_streaming_and_subscriptions_validates() {
    validate(Fixtures.streamingConfig(), null);
  }

  @Test
  void the_synthesized_stack_without_subscriptions_validates() {
    validate(Fixtures.noSubscriptionsConfig(), null);
  }

  @Test
  void the_synthesized_stack_with_rockets_waf_and_basic_plan_validates() {
    EnvironmentVariables.set("BOOSTER_USE_WAF", "true");
    EnvironmentVariables.set("BOOSTER_AZURE_SERVICE_PLAN_BASIC", "true");
    BoosterConfig config = Fixtures.config();
    config.rockets = TestRockets.descriptors();
    validate(config, List.of(new TestRockets.WebhookRocket(), new TestRockets.FilesRocket()));
  }

  static Stream<Path> goldenStacks() throws IOException {
    List<Path> golden = list(GOLDEN);
    List<Path> rocketGolden;
    try (Stream<Path> rockets = Files.list(ROCKETS)) {
      rocketGolden = rockets.map(rocket -> rocket.resolve("src/test/resources/golden")).filter(Files::isDirectory)
          .flatMap(directory -> list(directory).stream()).toList();
    }
    // the webhook and file-uploads rockets: a moved golden directory must not silently drop them from the check
    assertThat(rocketGolden).extracting(path -> path.getFileName().toString())
        .contains("webhook.cdk.tf.json", "file-uploads.cdk.tf.json");
    return Stream.concat(golden.stream(), rocketGolden.stream());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("goldenStacks")
  void every_golden_stack_validates(Path golden) throws IOException {
    Path stackDirectory = Files.createDirectories(root.resolve("golden"));
    Files.copy(golden, stackDirectory.resolve("cdk.tf.json"));
    terraformValidate(stackDirectory);
  }

  private void validate(BoosterConfig config, List<InfrastructureRocket> rockets) {
    ApplicationBuild build = new ApplicationBuilder(config, rockets,
        Fixtures.context(root, new RecordingCommandRunner(), new FakeHttpTransport(), new RecordingSleeper()))
        .buildApplication();
    terraformValidate(build.stackDirectory());
  }

  private static void terraformValidate(Path stackDirectory) {
    String terraform = Constants.terraformBinary();
    Map<String, String> environment = Map.of("TF_IN_AUTOMATION", "true");
    assertThatCode(() -> {
      ProcessCommandRunner.INSTANCE.run(new Command(stackDirectory,
          List.of(terraform, "init", "-backend=false", "-input=false"), environment, false));
      ProcessCommandRunner.INSTANCE.run(new Command(stackDirectory,
          List.of(terraform, "validate", "-no-color"), environment, false));
    }).doesNotThrowAnyException();
  }

  private static List<Path> list(Path directory) {
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(path -> path.getFileName().toString().endsWith(".cdk.tf.json")).sorted().toList();
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
