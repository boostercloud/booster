package com.boostercloud.rocket.webhook.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.application.tester.TokenHelper;
import com.boostercloud.application.tester.TokenOptions;
import com.boostercloud.framework.core.services.tokenverifiers.PublicKeyTokenVerifier;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.BoosterTokenExpiredError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.rocket.webhook.types.WebhookHttpRequest;
import com.boostercloud.rocket.webhook.types.WebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code webhook-token-verifier.ts}: the {@code authorization} header (with its {@code Bearer } prefix) goes through
 * {@code BoosterTokenVerifier}; without it the request is anonymous.
 */
class WebhookTokenVerifierTest {

  private BoosterConfig config;
  private final TokenHelper tokens = new TokenHelper();

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.tokenVerifiers.add(new PublicKeyTokenVerifier("booster", TokenHelper.publicKeyPem(), "booster:role"));
  }

  private static WebhookRequest withAuthorization(String value) {
    WebhookHttpRequest.Builder req = WebhookHttpRequest.builder("POST", "/test");
    if (value != null) {
      req.header("Authorization", value);
    }
    return WebhookRequest.of(req.build());
  }

  @Test
  void a_request_without_token_is_anonymous() {
    assertThat(WebhookTokenVerifier.verify(config, withAuthorization(null))).isNull();
    assertThat(WebhookTokenVerifier.verify(config, withAuthorization(""))).isNull();
    assertThat(WebhookTokenVerifier.verify(config, WebhookRequest.from(java.util.Map.of()))).isNull();
  }

  @Test
  void a_valid_bearer_token_gives_its_user() {
    String token = tokens.forUser("admin@example.com", "Admin");

    UserEnvelope user = WebhookTokenVerifier.verify(config, withAuthorization("Bearer " + token));

    assertThat(user.username()).isEqualTo("admin@example.com");
    assertThat(user.roles()).containsExactly("Admin");
  }

  @Test
  void invalid_and_expired_tokens_are_rejected_with_the_booster_errors() {
    assertThatThrownBy(() -> WebhookTokenVerifier.verify(config, withAuthorization("Bearer nonsense")))
        .isInstanceOf(NotAuthorizedError.class);
    String expired = tokens.forUser("admin@example.com", "Admin", TokenOptions.builder().expiresIn(0).build());
    assertThatThrownBy(() -> WebhookTokenVerifier.verify(config, withAuthorization("Bearer " + expired)))
        .isInstanceOf(BoosterTokenExpiredError.class).hasMessageContaining("jwt expired");
  }
}
