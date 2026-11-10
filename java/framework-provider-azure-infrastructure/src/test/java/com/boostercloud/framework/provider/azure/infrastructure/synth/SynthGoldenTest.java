package com.boostercloud.framework.provider.azure.infrastructure.synth;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.infrastructure.ApplicationBuild;
import com.boostercloud.framework.provider.azure.infrastructure.ApplicationBuilder;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * Golden-file contract tests of the synthesized {@code cdk.tf.json} for representative configurations. The project
 * root (temporary) is replaced by {@code <PROJECT_ROOT>} before comparing. Regenerate the files with
 * {@code -Dbooster.golden.update=true} and review the diff.
 */
@ExtendWith(TestEnvironment.class)
class SynthGoldenTest {

  private static final Path GOLDEN = Path.of("src/test/resources/golden");

  @TempDir Path root;

  @Test
  void synthesizes_an_application_without_subscriptions() throws IOException {
    assertGolden("no-subscriptions.cdk.tf.json", Fixtures.noSubscriptionsConfig(), null);
  }

  @Test
  void synthesizes_an_application_with_subscriptions() throws IOException {
    assertGolden("subscriptions.cdk.tf.json", Fixtures.config(), null);
  }

  @Test
  void synthesizes_an_application_with_event_streaming() throws IOException {
    assertGolden("event-stream.cdk.tf.json", Fixtures.streamingConfig(), null);
  }

  @Test
  void synthesizes_an_application_with_rockets_waf_and_basic_plan() throws IOException {
    EnvironmentVariables.set("BOOSTER_USE_WAF", "true");
    EnvironmentVariables.set("BOOSTER_AZURE_SERVICE_PLAN_BASIC", "true");
    BoosterConfig config = Fixtures.config();
    config.rockets = TestRockets.descriptors();
    assertGolden("rockets.cdk.tf.json", config,
        List.of(new TestRockets.WebhookRocket(), new TestRockets.FilesRocket()));
  }

  private void assertGolden(String goldenName, BoosterConfig config, List<InfrastructureRocket> rockets)
      throws IOException {
    ApplicationBuild build = new ApplicationBuilder(config, rockets,
        Fixtures.context(root, new RecordingCommandRunner(), new FakeHttpTransport(), new RecordingSleeper()))
        .buildApplication();
    Path stackFile = build.stackDirectory().resolve("cdk.tf.json");
    assertThat(build.stackDirectory())
        .isEqualTo(root.toAbsolutePath().normalize().resolve("cdktf.out/stacks/my-storeproduction"));
    String actual = Files.readString(stackFile, StandardCharsets.UTF_8)
        .replace(root.toAbsolutePath().normalize().toString(), "<PROJECT_ROOT>");
    Path golden = GOLDEN.resolve(goldenName);
    if (Boolean.getBoolean("booster.golden.update")) {
      Files.createDirectories(GOLDEN);
      Files.writeString(golden, actual, StandardCharsets.UTF_8);
    }
    assertThat(actual).isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
  }
}
