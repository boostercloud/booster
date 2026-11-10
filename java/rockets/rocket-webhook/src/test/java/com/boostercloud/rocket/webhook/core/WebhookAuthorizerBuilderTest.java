package com.boostercloud.rocket.webhook.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.Authorize;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.rocket.webhook.types.WebhookAuthorizer;
import com.boostercloud.rocket.webhook.types.WebhookHttpRequest;
import com.boostercloud.rocket.webhook.types.WebhookRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@code webhook-authorizer-builder.ts} and {@code webhook-authorization-checker.ts}: {@code 'all'}, role lists, custom
 * functions, the deny-by-default branch and "no {@code authorize} = no check".
 */
class WebhookAuthorizerBuilderTest {

  static final class Admin {}

  static final class User {}

  private final WebhookRequest request = WebhookRequest.of(WebhookHttpRequest.builder("POST", "/test").build());

  private static UserEnvelope userWith(String... roles) {
    return UserEnvelope.builder().id("1").username("u@example.com").roles(List.of(roles)).build();
  }

  @Test
  void all_allows_everybody() {
    WebhookAuthorizer authorizer = WebhookAuthorizerBuilder.build(Authorize.all());

    assertThatCode(() -> authorizer.authorize(null, request)).doesNotThrowAnyException();
    assertThatCode(() -> authorizer.authorize(userWith(), request)).doesNotThrowAnyException();
  }

  @Test
  void roles_match_the_simple_class_names_of_the_token_roles() {
    WebhookAuthorizer authorizer = WebhookAuthorizerBuilder.build(Authorize.roles(Admin.class, User.class));

    assertThatCode(() -> authorizer.authorize(userWith("User"), request)).doesNotThrowAnyException();
    assertThatThrownBy(() -> authorizer.authorize(userWith("Guest"), request))
        .isInstanceOf(NotAuthorizedError.class).hasMessage("Access denied for this resource");
    assertThatThrownBy(() -> authorizer.authorize(null, request))
        .isInstanceOf(NotAuthorizedError.class).hasMessage("Access denied for this resource");
    assertThatThrownBy(() -> WebhookAuthorizerBuilder.build(Authorize.roles()).authorize(userWith("Admin"), request))
        .isInstanceOf(NotAuthorizedError.class);
  }

  @Test
  void a_custom_function_is_used_as_is() {
    WebhookAuthorizer custom = (user, req) -> {};

    assertThat(WebhookAuthorizerBuilder.build(Authorize.custom(custom))).isSameAs(custom);
  }

  @Test
  void anything_else_denies_access() {
    WebhookAuthorizer fromUnknownCustom = WebhookAuthorizerBuilder.build(Authorize.custom("not a function"));

    assertThatThrownBy(() -> fromUnknownCustom.authorize(userWith("Admin"), request))
        .isInstanceOf(NotAuthorizedError.class).hasMessage("Access denied for this resource");
    assertThatThrownBy(() -> WebhookAuthorizerBuilder.build(null).authorize(null, request))
        .isInstanceOf(NotAuthorizedError.class);
  }

  @Test
  void the_checker_does_nothing_without_authorize_and_rebuilds_the_authorizer_on_every_call() {
    AtomicInteger calls = new AtomicInteger();
    Authorize<WebhookAuthorizer> counting = Authorize.custom((user, req) -> calls.incrementAndGet());
    BoosterConfig config = new BoosterConfig("test");

    assertThatCode(() -> WebhookAuthorizationChecker.authorize(config, null, null, request)).doesNotThrowAnyException();
    WebhookAuthorizationChecker.authorize(config, counting, null, request);
    WebhookAuthorizationChecker.authorize(null, counting, null, request);
    assertThat(calls).hasValue(2);
    assertThatThrownBy(() -> WebhookAuthorizationChecker.authorize(config, Authorize.roles(Admin.class), null,
        request)).isInstanceOf(NotAuthorizedError.class);
  }
}
