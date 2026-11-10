package com.boostercloud.rocket.webhook.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.framework.types.rockets.RocketFunction;
import com.boostercloud.rocket.webhook.types.WebhookAPIErrorResult;
import com.boostercloud.rocket.webhook.types.WebhookAPIResult;
import com.boostercloud.rocket.webhook.types.WebhookHttpRequest;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code rocket-webhook-core/src/index.ts} ({@code BoosterWebhook}): the rocket function registration and the
 * descriptors (characterisation tests: the TypeScript repository has none).
 */
class BoosterWebhookTest {

  public static final class Handler {
    public static void handle() {}
  }

  private BoosterConfig config;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = Logger.NOOP;
  }

  @Test
  void registers_the_rocket_function_under_rocket_webhook() {
    new BoosterWebhook(config, List.of(WebhookParamsEvent.of("test", Handler.class)));

    assertThat(config.getRegisteredRocketFunction("rocket-webhook")).isPresent();
  }

  @Test
  void a_second_webhook_on_the_same_configuration_fails_with_the_typescript_message() {
    new BoosterWebhook(config, List.of());

    assertThatThrownBy(() -> new BoosterWebhook(config, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Error registering rocket function with id rocket-webhook: There is already a rocket function "
            + "registered under the same ID, \"\"");
  }

  @Test
  void the_descriptors_name_the_java_infrastructure_packages_and_share_the_params_instance() {
    List<WebhookParamsEvent> params = new ArrayList<>(List.of(WebhookParamsEvent.of("test", Handler.class)));
    BoosterWebhook webhook = new BoosterWebhook(config, params);

    RocketDescriptor local = webhook.rocketForLocal();
    RocketDescriptor azure = webhook.rocketForAzure();

    assertThat(local.packageName()).isEqualTo("com.boostercloud.rocket.webhook.local");
    assertThat(azure.packageName()).isEqualTo("com.boostercloud.rocket.webhook.azure");
    assertThat(local.parameters()).isSameAs(params);
    assertThat(azure.parameters()).isSameAs(params);
    assertThat(webhook.rocketForLocal()).isEqualTo(local);
    assertThat(webhook.params()).isSameAs(params);
    assertThat(webhook.config()).isSameAs(config);
  }

  @Test
  void the_registered_function_uses_the_config_it_is_called_with_and_sees_later_changes_of_the_params() {
    List<WebhookParamsEvent> params = new ArrayList<>();
    new BoosterWebhook(config, params);
    RocketFunction function = config.getRegisteredRocketFunction("rocket-webhook").orElseThrow();
    Map<String, Object> request = Map.of("req", WebhookHttpRequest.builder("POST", "/late").build());

    assertThat(((WebhookAPIErrorResult) function.apply(config, request)).body().reason())
        .isEqualTo("Could not find route late in parameters.");

    params.add(WebhookParamsEvent.of("late", Handler.class));
    BoosterConfig other = new BoosterConfig("other");
    other.logger = Logger.NOOP;
    WebhookAPIResult result = (WebhookAPIResult) function.apply(other, request);
    assertThat(result.status()).isEqualTo(200);
  }

  @Test
  void parameters_are_not_validated_when_the_rocket_is_created() {
    List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.builder().build(),
        WebhookParamsEvent.of("a", Handler.class), WebhookParamsEvent.of("a", Handler.class));

    assertThat(new BoosterWebhook(config, params).params()).hasSize(3);
  }
}
