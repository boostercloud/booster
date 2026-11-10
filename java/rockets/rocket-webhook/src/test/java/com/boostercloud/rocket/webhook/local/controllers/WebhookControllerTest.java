package com.boostercloud.rocket.webhook.local.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.local.infrastructure.web.HttpRequest;
import com.boostercloud.framework.provider.local.infrastructure.web.MemoryHttpResponse;
import com.boostercloud.rocket.webhook.types.WebhookAPIErrorResult;
import com.boostercloud.rocket.webhook.types.WebhookAPISuccessResult;
import com.boostercloud.rocket.webhook.types.WebhookHttpRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * {@code controllers/webhook-controller.ts}: the two routes of a webhook, the request handed to
 * {@code boosterRocketDispatcher} and how its result is written back.
 */
class WebhookControllerTest {

  private final List<Object> dispatched = new ArrayList<>();
  private final AtomicReference<Throwable> nextError = new AtomicReference<>();
  private boolean nextCalled;

  private MemoryHttpResponse call(WebhookController controller, HttpRequest request) {
    MemoryHttpResponse response = new MemoryHttpResponse("HEAD".equals(request.method()));
    controller.router().handle(request, response, error -> {
      nextCalled = true;
      nextError.set(error);
    });
    return response;
  }

  private WebhookController controller(Object result) {
    return new WebhookController("test", request -> {
      dispatched.add(request);
      return result;
    });
  }

  private static Map<String, Object> headers(Object... keysAndValues) {
    Map<String, Object> headers = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      headers.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return headers;
  }

  private static String header(MemoryHttpResponse response, String name) {
    return response.sentHeaders().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue).findFirst().orElse(null);
  }

  private static final WebhookAPISuccessResult EMPTY =
      WebhookAPISuccessResult.of(headers("Access-Control-Allow-Origin", "*", "Content-Type", "text/plain"), "");

  @Test
  void post_and_get_reach_the_dispatcher_with_the_rocket_id_and_the_request() throws Exception {
    WebhookController controller = controller(EMPTY);
    HttpRequest post = HttpRequest.builder("POST", "/test?param1=testvalue&list[]=a&list[]=b")
        .header("Host", "localhost:3000").body("text/plain", "hello").build();

    call(controller, post);
    call(controller, HttpRequest.builder("GET", "/test").build());

    assertThat(dispatched).hasSize(2);
    @SuppressWarnings("unchecked")
    Map<String, Object> request = (Map<String, Object>) dispatched.getFirst();
    assertThat(List.copyOf(request.keySet())).containsExactly("BOOSTER_ROCKET_FUNCTION_ID", "req");
    assertThat(request.get("BOOSTER_ROCKET_FUNCTION_ID")).isEqualTo("rocket-webhook");
    WebhookHttpRequest req = (WebhookHttpRequest) request.get("req");
    assertThat(req.method()).isEqualTo("POST");
    assertThat(req.url()).isEqualTo("/test?param1=testvalue&list[]=a&list[]=b");
    assertThat(req.originalUrl()).isEqualTo(req.url());
    assertThat(req.headers()).containsEntry("host", "localhost:3000").containsEntry("content-type", "text/plain");
    assertThat(req.query()).isEqualTo(Map.of("param1", "testvalue", "list", List.of("a", "b")));
    assertThat(req.params()).isEmpty();
    assertThat(req.rawBody()).isNull();
    assertThat(req.body()).isEqualTo(Map.of());
    assertThat(new String(req.bodyStream().get().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    assertThat(((WebhookHttpRequest) ((Map<?, ?>) dispatched.get(1)).get("req")).method()).isEqualTo("GET");
  }

  @Test
  void octet_stream_bodies_arrive_as_bytes_through_express_raw() throws Exception {
    byte[] bytes = {1, 2, 3};
    call(controller(EMPTY), HttpRequest.builder("POST", "/test").header("content-type", "application/octet-stream")
        .body(bytes).build());

    WebhookHttpRequest req = (WebhookHttpRequest) ((Map<?, ?>) dispatched.getFirst()).get("req");
    assertThat(req.body()).isEqualTo(bytes);
    assertThat(req.bodyStream().get().readAllBytes()).isEmpty();
  }

  @Test
  void other_methods_and_paths_are_not_handled_but_express_path_rules_apply() {
    WebhookController controller = controller(EMPTY);

    call(controller, HttpRequest.builder("PUT", "/test").build());
    assertThat(dispatched).isEmpty();
    assertThat(nextCalled).isTrue();
    assertThat(nextError.get()).isNull();

    call(controller, HttpRequest.builder("POST", "/TEST/").build());
    MemoryHttpResponse head = call(controller, HttpRequest.builder("HEAD", "/test").build());
    assertThat(dispatched).extracting(request -> ((WebhookHttpRequest) ((Map<?, ?>) request).get("req")).method())
        .containsExactly("POST", "HEAD");
    assertThat(head.sentStatus()).isEqualTo(200);
  }

  @Test
  void a_json_success_sends_every_header_and_the_body_as_utf8_text() {
    MemoryHttpResponse response = call(controller(WebhookAPISuccessResult.of(
        headers("Access-Control-Allow-Origin", "*", "Test", "test header", "Content-Type", "application/json"),
        "{\"name\":\"my_name\"}")), HttpRequest.builder("POST", "/test").build());

    assertThat(response.sentStatus()).isEqualTo(200);
    assertThat(header(response, "Access-Control-Allow-Origin")).isEqualTo("*");
    assertThat(header(response, "Test")).isEqualTo("test header");
    assertThat(header(response, "Content-Type")).isEqualTo("application/json; charset=utf-8");
    assertThat(response.bodyAsString()).isEqualTo("{\"name\":\"my_name\"}");
  }

  @Test
  void text_empty_and_object_bodies_go_through_res_send() {
    MemoryHttpResponse empty = call(controller(EMPTY), HttpRequest.builder("POST", "/test").build());
    MemoryHttpResponse object = call(controller(WebhookAPISuccessResult.of(
        headers("Content-Type", "text/plain"), Map.of("a", 1))), HttpRequest.builder("POST", "/test").build());

    assertThat(empty.sentStatus()).isEqualTo(200);
    assertThat(header(empty, "Content-Type")).isEqualTo("text/plain; charset=utf-8");
    assertThat(empty.bodyBytes()).isEmpty();
    assertThat(header(object, "Content-Type")).isEqualTo("text/plain; charset=utf-8");
    assertThat(object.bodyAsString()).isEqualTo("{\"a\":1}");
  }

  @Test
  void a_file_success_sends_the_bytes_as_they_are_with_its_headers() {
    byte[] content = {0, (byte) 0xFF, 'a'};
    MemoryHttpResponse response = call(controller(WebhookAPISuccessResult.of(headers(
        "Access-Control-Allow-Origin", "*", "Content-Type", "image/png",
        "Content-Disposition", "attachment; filename=1.png"), content)), HttpRequest.builder("GET", "/test").build());

    assertThat(response.bodyBytes()).isEqualTo(content);
    assertThat(header(response, "Content-Type")).isEqualTo("image/png");
    assertThat(header(response, "Content-Disposition")).isEqualTo("attachment; filename=1.png");
    assertThat(header(response, "Content-Length")).isEqualTo("3");
  }

  @Test
  void a_file_without_mime_type_is_sent_as_octet_stream_like_express() {
    MemoryHttpResponse bytes = call(controller(WebhookAPISuccessResult.of(headers("Content-Type", "",
        "Content-Disposition", "attachment; filename=x"), new byte[] {1})), HttpRequest.builder("GET", "/test").build());
    MemoryHttpResponse text = call(controller(WebhookAPISuccessResult.of(headers("Content-Type", ""), "x")),
        HttpRequest.builder("GET", "/test").build());

    assertThat(header(bytes, "Content-Type")).isEqualTo("application/octet-stream");
    assertThat(header(text, "Content-Type")).isEqualTo("text/html; charset=utf-8");
  }

  @Test
  void a_handler_content_type_spelled_content_type_does_not_switch_to_res_json() {
    MemoryHttpResponse response = call(controller(WebhookAPISuccessResult.of(
        headers("Content-type", "application/json", "Content-Type", "application/json"), "{\"a\":1}")),
        HttpRequest.builder("POST", "/test").build());

    assertThat(response.bodyAsString()).isEqualTo("{\"a\":1}");
  }

  @Test
  void a_failure_sends_the_status_and_the_body_as_json_without_the_result_headers() {
    MemoryHttpResponse response = call(controller(new WebhookAPIErrorResult(400,
        new WebhookAPIErrorResult.Body("Invalid Parameter Error", "Error message"),
        headers("Access-Control-Allow-Origin", "*", "Content-Type", "application/json", "X-Extra", "1"))),
        HttpRequest.builder("POST", "/test").build());

    assertThat(response.sentStatus()).isEqualTo(400);
    assertThat(response.bodyAsString()).isEqualTo("{\"title\":\"Invalid Parameter Error\",\"reason\":\"Error message\"}");
    assertThat(header(response, "Content-Type")).isEqualTo("application/json; charset=utf-8");
    assertThat(header(response, "Access-Control-Allow-Origin")).isNull();
    assertThat(header(response, "X-Extra")).isNull();
  }

  @Test
  void raw_map_results_are_read_by_member_name() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("status", 200);
    raw.put("headers", headers("X-Raw", 7));
    raw.put("body", "raw");

    MemoryHttpResponse response = call(controller(raw), HttpRequest.builder("POST", "/test").build());

    assertThat(response.sentStatus()).isEqualTo(200);
    assertThat(header(response, "X-Raw")).isEqualTo("7");
    assertThat(response.bodyAsString()).isEqualTo("raw");
  }

  @Test
  void a_dispatcher_exception_is_answered_with_request_failed_and_passed_to_next() {
    IllegalStateException error = new IllegalStateException("Rocket function with ID \"rocket-webhook\" not found. "
        + "Did you forget registering the function with \"config.registerRocketFunction()\"?");
    Function<Object, Object> failing = request -> {
      throw error;
    };
    MemoryHttpResponse response = call(new WebhookController("test", failing),
        HttpRequest.builder("POST", "/test").body(new ByteArrayInputStream(new byte[0])).build());

    assertThat(response.sentStatus()).isEqualTo(500);
    assertThat(response.bodyAsJson()).isEqualTo(Map.of("title", "Illegal State Exception", "reason",
        error.getMessage()));
    assertThat(nextError.get()).isSameAs(error);
  }

  @Test
  void a_result_without_status_is_an_invalid_status_code() {
    MemoryHttpResponse response = call(controller(Map.of("body", "x")), HttpRequest.builder("POST", "/test").build());

    assertThat(response.sentStatus()).isEqualTo(500);
    assertThat(response.bodyAsJson()).isEqualTo(Map.of("title", "Illegal Argument Exception",
        "reason", "Invalid status code: undefined"));
  }

  @Test
  void the_endpoint_is_kept() {
    assertThat(new WebhookController("clients/other", request -> EMPTY).endpoint()).isEqualTo("clients/other");
  }
}
