package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.InvalidVersionError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.errors.NotFoundError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Port of {@code test/library/api-adapter.test.ts}, plus {@code requestSucceeded} and {@code healthRequestResult}. */
class ApiAdapterTest {

  private static final Map<String, String> DEFAULT_HEADERS =
      Map.of("Access-Control-Allow-Origin", "*", "Content-Type", "application/json");

  static Stream<Arguments> errors() {
    return Stream.of(
        Arguments.of(new InvalidParameterError("error message"), 400, "Invalid Parameter Error"),
        Arguments.of(new NotAuthorizedError("error message"), 401, "Not Authorized Error"),
        Arguments.of(new NotFoundError("error message"), 404, "Not Found Error"),
        Arguments.of(new InvalidVersionError("error message"), 422, "Invalid Version Error"));
  }

  @Nested
  class TheRequestFailedMethod {
    @ParameterizedTest(name = "returns the proper body for error ''{0}''")
    @MethodSource("com.boostercloud.framework.provider.azure.library.ApiAdapterTest#errors")
    void returnsTheProperBodyForTheError(Throwable input, int status, String title) {
      AzureHttpResponse got = ApiAdapter.requestFailed(input);

      assertThat(got.status()).isEqualTo(status);
      // jsonBody is an object, not a JSON string
      assertThat(got.jsonBody()).isEqualTo(obj("status", status, "title", title, "reason", "error message"));
      assertThat(got.headers()).isEqualTo(DEFAULT_HEADERS);
    }

    @Test
    void anyOtherErrorIsA500() {
      AzureHttpResponse got = ApiAdapter.requestFailed(new IllegalStateException("boom"));

      assertThat(got.status()).isEqualTo(500);
      assertThat(got.jsonBody()).isEqualTo(obj("status", 500, "title", "Illegal State Exception", "reason", "boom"));
    }
  }

  @Nested
  class TheRequestSucceededMethod {
    @Test
    void aWebSocketProtocolHeaderAnswersTheWebPubSubConnect() {
      assertThat(ApiAdapter.requestSucceeded(null, Map.of("Sec-WebSocket-Protocol", "graphql-ws")))
          .isEqualTo(new WebPubSubConnectResponse("graphql-ws", null, null, null));
      assertThat(ApiAdapter.requestSucceeded(null, Map.of("Sec-WebSocket-Protocol", List.of("a", "b"))))
          .isEqualTo(new WebPubSubConnectResponse("a", null, null, null));
      // The header name is case-sensitive
      assertThat(ApiAdapter.requestSucceeded(null, Map.of("sec-websocket-protocol", "graphql-ws")))
          .isInstanceOf(AzureHttpResponse.class);
    }

    @Test
    void theWebSocketMessageMarkerAnswersTheWebPubSubMessage() {
      Map<String, Object> body = obj("type", "data", "id", "1");

      assertThat(ApiAdapter.requestSucceeded(body, Map.of("X-Booster-WebSocket-Message", "true")))
          .isEqualTo(new WebPubSubMessageResponse(body, "json"));
      assertThat(ApiAdapter.requestSucceeded(body, Map.of("X-Booster-WebSocket-Message", true)))
          .isInstanceOf(AzureHttpResponse.class);
    }

    @Test
    void noBodyAndNoHeadersIsNothing() {
      assertThat(ApiAdapter.requestSucceeded(null, null)).isNull();
      assertThat(ApiAdapter.requestSucceeded("", Map.of())).isNull();
    }

    @Test
    void anythingElseIsAnHttpResponseWithoutInternalHeaders() {
      Map<String, Object> headers = new LinkedHashMap<>();
      headers.put("Content-Type", "text/plain");
      headers.put("X-Booster-Internal", "x");
      headers.put("Cache", List.of("a", "b"));
      headers.put("Count", 3);
      Map<String, Object> body = obj("data", obj("x", 1));

      AzureHttpResponse got = (AzureHttpResponse) ApiAdapter.requestSucceeded(body, headers);

      assertThat(got.status()).isEqualTo(200);
      assertThat(got.headers()).containsExactly(
          Map.entry("Access-Control-Allow-Origin", "*"), Map.entry("Content-Type", "text/plain"),
          Map.entry("Cache", "a,b"), Map.entry("Count", "3"));
      assertThat(got.jsonBody()).isEqualTo(body);
    }

    @Test
    void aFalsyBodyWithHeadersHasNoJsonBody() {
      AzureHttpResponse got = (AzureHttpResponse) ApiAdapter.requestSucceeded(0, Map.of("A", "b"));

      assertThat(got.jsonBody()).isNull();
      assertThat(got.headers()).containsEntry("A", "b");
    }
  }

  @Nested
  class TheHealthRequestResultMethod {
    @Test
    void healthyIs200AndUnhealthyIs503() {
      List<Object> tree = List.of(obj("id", "booster", "status", "UP"));

      assertThat(ApiAdapter.healthRequestResult(tree, true)).isEqualTo(new AzureHttpResponse(DEFAULT_HEADERS, 200, tree));
      assertThat(ApiAdapter.healthRequestResult(tree, false).status()).isEqualTo(503);
    }
  }
}
