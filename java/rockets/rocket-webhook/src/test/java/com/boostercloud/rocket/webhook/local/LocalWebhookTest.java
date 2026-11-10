package com.boostercloud.rocket.webhook.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.common.RocketLoader;
import com.boostercloud.framework.provider.local.infrastructure.InfrastructureRocketMetadata;
import com.boostercloud.framework.provider.local.infrastructure.LocalInfrastructureRocket;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpRequest;
import com.boostercloud.framework.provider.local.infrastructure.web.MemoryHttpResponse;
import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.rocket.webhook.core.BoosterWebhook;
import com.boostercloud.rocket.webhook.types.WebhookAPISuccessResult;
import com.boostercloud.rocket.webhook.types.WebhookHttpRequest;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code rocket-webhook-local-infrastructure/src/index.ts} and {@code infra.ts}: the rocket loaded from its descriptor
 * package, and one controller per webhook mounted at {@code /} of the server router.
 */
class LocalWebhookTest {

  static final class Handler {}

  private final List<String> dispatchedUrls = new ArrayList<>();

  private MemoryHttpResponse call(Router router, String method, String url) {
    MemoryHttpResponse response = new MemoryHttpResponse();
    router.handle(HttpRequest.builder(method, url).build(), response, error -> response.status(404).send("next"));
    return response;
  }

  private Object fakeDispatcher(Object request) {
    dispatchedUrls.add(((WebhookHttpRequest) ((Map<?, ?>) request).get("req")).url());
    return WebhookAPISuccessResult.of(Map.of("Content-Type", "text/plain"), "ok");
  }

  @Test
  void the_rocket_loader_builds_the_local_webhook_from_the_descriptor_package() {
    BoosterConfig config = new BoosterConfig("local");
    List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("test", Handler.class));
    RocketDescriptor descriptor = new BoosterWebhook(config, params).rocketForLocal();

    LocalInfrastructureRocket rocket = RocketLoader.loadRocket(descriptor, LocalInfrastructureRocket.class);

    assertThat(rocket).isInstanceOf(LocalWebhook.class);
    assertThat(((LocalWebhook) rocket).params()).isEqualTo(params);
  }

  @Test
  void descriptor_parameters_that_are_not_webhooks_are_rejected() {
    assertThatThrownBy(() -> new Rocket().build(Map.of("route", "test")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("The parameters of the webhook rocket must be a List<WebhookParamsEvent>");
  }

  @Test
  void mounts_every_webhook_at_its_route_without_prefix_and_ignores_the_metadata() {
    Router router = new Router();
    List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("test", Handler.class),
        WebhookParamsEvent.of("clients/other", Handler.class));

    Infra.mountStack(params, router, this::fakeDispatcher);

    assertThat(router.size()).isEqualTo(2);
    assertThat(call(router, "POST", "/test").bodyAsString()).isEqualTo("ok");
    assertThat(call(router, "GET", "/clients/other?x=1").bodyAsString()).isEqualTo("ok");
    assertThat(call(router, "POST", "/webhook/test").bodyAsString()).isEqualTo("next");
    assertThat(call(router, "POST", "/clients").bodyAsString()).isEqualTo("next");
    assertThat(dispatchedUrls).containsExactly("/test", "/clients/other?x=1");
  }

  @Test
  void the_local_webhook_mounts_through_the_spi_and_a_webhook_without_route_aborts_the_mount() {
    Router router = new Router();
    new LocalWebhook(List.of(WebhookParamsEvent.of("a", Handler.class))).mountStack(new BoosterConfig("local"), router,
        new InfrastructureRocketMetadata(3000));
    assertThat(router.size()).isEqualTo(1);

    LocalWebhook broken = new LocalWebhook(List.of(WebhookParamsEvent.builder().handlerClass(Handler.class).build()));
    assertThatThrownBy(() -> broken.mountStack(new BoosterConfig("local"), new Router(), null))
        .isInstanceOf(IllegalStateException.class).hasMessage("Undefined route parameter [object Object]");
  }
}
