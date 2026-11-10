package com.boostercloud.rocket.webhook.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.Authorize;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code webhook-params.ts}: the function id, {@code getRoute} and the descriptor parameters. */
@SuppressWarnings("deprecation")
class WebhookParamsTest {

  static final class Handler {}

  static final class Admin {}

  @Test
  void the_function_id_is_rocket_webhook() {
    assertThat(WebhookParams.FUNCTION_ID).isEqualTo("rocket-webhook");
  }

  @Test
  void the_route_wins_over_the_deprecated_origin() {
    assertThat(WebhookParams.getRoute(WebhookParamsEvent.builder().route("a").origin("b").build())).isEqualTo("a");
    assertThat(WebhookParams.getRoute(WebhookParamsEvent.builder().origin("b").build())).isEqualTo("b");
  }

  @Test
  void routes_are_not_normalised() {
    assertThat(WebhookParams.getRoute(WebhookParamsEvent.of("/clients/Other/ ", Handler.class)))
        .isEqualTo("/clients/Other/ ");
  }

  @Test
  void a_webhook_without_route_fails_and_an_empty_route_does_not_fall_back_to_the_origin() {
    assertThatThrownBy(() -> WebhookParams.getRoute(WebhookParamsEvent.builder().handlerClass(Handler.class).build()))
        .isInstanceOf(IllegalStateException.class).hasMessage("Undefined route parameter [object Object]");
    assertThatThrownBy(() -> WebhookParams.getRoute(WebhookParamsEvent.builder().route("").origin("x").build()))
        .hasMessage("Undefined route parameter [object Object]");
  }

  @Test
  void reads_the_descriptor_parameters() {
    WebhookParamsEvent first = WebhookParamsEvent.of("a", Handler.class);
    List<WebhookParamsEvent> params = new ArrayList<>(List.of(first));
    assertThat(WebhookParams.from(params)).containsExactly(first);
    assertThatThrownBy(() -> WebhookParams.from(null)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a List<WebhookParamsEvent>, got undefined");
    assertThatThrownBy(() -> WebhookParams.from(Map.of("route", "a")))
        .hasMessageContaining("got java.util.");
    assertThatThrownBy(() -> WebhookParams.from(List.of(Map.of("route", "a"))))
        .hasMessageContaining("got an element of type");
  }

  @Test
  void the_builder_sets_every_option() {
    MultiPartConfig multipart = MultiPartConfig.of(MultiPartConfig.Limits.fileSize(10).withFiles(2L).withParts(3L));
    WebhookAuthorizer authorizer = (user, request) -> {};
    WebhookParamsEvent param = WebhookParamsEvent.builder()
        .route("clients/other")
        .handlerClass(Handler.class)
        .allowedMethods(AllowedHttpMethod.GET, AllowedHttpMethod.POST)
        .multiPartConfig(multipart)
        .authorize(authorizer)
        .build();
    assertThat(param.route()).isEqualTo("clients/other");
    assertThat(param.handlerClass()).isEqualTo(Handler.class);
    assertThat(param.allowedMethods()).containsExactly(AllowedHttpMethod.GET, AllowedHttpMethod.POST);
    assertThat(param.multiPartConfig().limits()).isEqualTo(new MultiPartConfig.Limits(10L, 2L, 3L));
    assertThat(param.authorize()).isEqualTo(Authorize.custom(authorizer));
    assertThat(param.toBuilder().authorizeAll().build().authorize()).isInstanceOf(Authorize.All.class);
    assertThat(param.toBuilder().authorizeRoles(Admin.class).build().authorize())
        .isEqualTo(Authorize.roles(Admin.class));
    assertThat(WebhookParamsEvent.of("x", Handler.class).allowedMethods()).isNull();
    assertThat(param.toBuilder().allowedMethods(List.of()).build().allowedMethods()).isEmpty();
  }

  @Test
  void enums_keep_the_typescript_values() {
    assertThat(AllowedHttpMethod.GET.value()).isEqualTo("GET");
    assertThat(AllowedHttpMethod.POST.value()).isEqualTo("POST");
    assertThat(WebhookResponseType.TEXT.value()).isEqualTo("text/plain");
    assertThat(WebhookResponseType.JSON.value()).isEqualTo("application/json");
    assertThat(WebhookResponseType.FILE.value()).isEqualTo("file");
    assertThat(WebhookResponseType.fromValue("application/json")).isEqualTo(WebhookResponseType.JSON);
    assertThat(WebhookResponseType.fromValue(WebhookResponseType.FILE)).isEqualTo(WebhookResponseType.FILE);
    assertThat(WebhookResponseType.fromValue("text/html")).isNull();
    assertThat(WebhookAPIResult.HTTP_SUCCESS_STATUS_CODE).isEqualTo(200);
  }
}
