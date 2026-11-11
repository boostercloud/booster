package com.boostercloud.rocket.webhook.core.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.errors.BoosterTokenExpiredError;
import com.boostercloud.framework.types.errors.BoosterTokenNotBeforeError;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.InvalidProtocolError;
import com.boostercloud.framework.types.errors.InvalidVersionError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.rocket.webhook.types.WebhookAPIErrorResult;
import com.boostercloud.rocket.webhook.types.WebhookAPISuccessResult;
import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@code webhook-response.ts}: success results are always 200; failures map the error class to a status. */
class WebhookResponseTest {

  /** A subclass of a mapped error: TypeScript matches the exact class name, so it is a 500. */
  static final class SpecialNotFoundError extends NotFoundError {
    SpecialNotFoundError(String message) {
      super(message);
    }
  }

  static java.util.stream.Stream<Arguments> errors() {
    return java.util.stream.Stream.of(
        Arguments.of(new InvalidParameterError("Error message"), 400, "Invalid Parameter Error"),
        Arguments.of(new InvalidProtocolError("bad"), 400, "Invalid Protocol Error"),
        Arguments.of(new NotAuthorizedError("Access denied for this resource"), 401, "Not Authorized Error"),
        Arguments.of(new BoosterTokenExpiredError("jwt expired"), 401, "Booster Token Expired Error"),
        Arguments.of(new BoosterTokenNotBeforeError("jwt not active"), 401, "Booster Token Not Before Error"),
        Arguments.of(new NotFoundError("missing"), 404, "Not Found Error"),
        Arguments.of(new InvalidVersionError("v"), 422, "Invalid Version Error"),
        Arguments.of(new SpecialNotFoundError("sub"), 500, "Special Not Found Error"),
        Arguments.of(new IllegalStateException("x"), 500, "Illegal State Exception"),
        Arguments.of(new RuntimeException((String) null), 500, "Runtime Exception"));
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("errors")
  void failures_map_the_error_class_to_status_and_title(Throwable error, int status, String title) {
    WebhookAPIErrorResult result = WebhookResponse.responseFailure(error);

    assertThat(result.status()).isEqualTo(status);
    assertThat(result.body()).isEqualTo(new WebhookAPIErrorResult.Body(title,
        error.getMessage() == null ? "" : error.getMessage()));
    assertThat(result.headers()).containsExactly(Map.entry("Access-Control-Allow-Origin", "*"),
        Map.entry("Content-Type", "application/json"));
  }

  @Test
  void the_failure_body_serialises_as_title_then_reason() {
    WebhookAPIErrorResult result = WebhookResponse.responseFailure(new InvalidParameterError("Error message"));

    assertThat(BoosterJson.toJson(result.body()))
        .isEqualTo("{\"title\":\"Invalid Parameter Error\",\"reason\":\"Error message\"}");
  }

  @Test
  void successes_are_200_with_the_builder_body_and_headers() {
    WebhookAPISuccessResult empty = WebhookResponse.responseSuccess();
    WebhookAPISuccessResult json = WebhookResponse.responseSuccess(WebhookHandlerReturnType.json(Map.of("a", 1)));

    assertThat(empty).isEqualTo(new WebhookAPISuccessResult(Map.of("Access-Control-Allow-Origin", "*",
        "Content-Type", "text/plain"), 200, ""));
    assertThat(json.status()).isEqualTo(200);
    assertThat(json.body()).isEqualTo("{\"a\":1}");
    assertThat(json.headers()).containsEntry("Content-Type", "application/json");
  }

  @Test
  void the_result_records_copy_their_headers_and_a_success_is_always_200() {
    java.util.LinkedHashMap<String, Object> headers = new java.util.LinkedHashMap<>(Map.of("a", "1"));
    WebhookAPISuccessResult result = WebhookAPISuccessResult.of(headers, "b");
    headers.put("c", "2");

    assertThat(result.headers()).containsOnlyKeys("a");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new WebhookAPISuccessResult(Map.of(), 201, ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
