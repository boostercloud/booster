package com.boostercloud.rocket.webhook.core.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.Logger;
import com.boostercloud.rocket.webhook.core.response.responses.WebhookEmptyResponse;
import com.boostercloud.rocket.webhook.core.response.responses.WebhookFileResponse;
import com.boostercloud.rocket.webhook.core.response.responses.WebhookJsonResponse;
import com.boostercloud.rocket.webhook.core.response.responses.WebhookTextResponse;
import com.boostercloud.rocket.webhook.types.WebhookHandlerFileReturnType;
import com.boostercloud.rocket.webhook.types.WebhookHandlerJsonReturnType;
import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import com.boostercloud.rocket.webhook.types.WebhookHandlerTextReturnType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code webhook-response-builder.ts} and the four strategies of {@code response/responses/}: bodies, type-specific
 * headers and the header merge order (default &lt; handler &lt; type).
 */
class WebhookResponseBuilderTest {

  private final List<String> logged = new ArrayList<>();
  private final Logger logger = new Logger() {
    @Override
    public void debug(Object message, Object... optionalParams) {}

    @Override
    public void info(Object message, Object... optionalParams) {
      logged.add(String.valueOf(message));
    }

    @Override
    public void warn(Object message, Object... optionalParams) {}

    @Override
    public void error(Object message, Object... optionalParams) {}
  };

  private WebhookResponseBuilder builder(Object response) {
    return new WebhookResponseBuilder(response, logger);
  }

  @Test
  void falsy_results_use_the_empty_strategy() {
    for (Object falsy : new Object[] {null, false, 0, 0L, 0.0, Double.NaN, ""}) {
      WebhookResponseBuilder builder = builder(falsy);
      assertThat(builder.strategy()).isInstanceOf(WebhookEmptyResponse.class);
      assertThat(builder.getBody()).isEqualTo("");
      assertThat(builder.getHeaders()).containsExactly(Map.entry("Access-Control-Allow-Origin", "*"),
          Map.entry("Content-Type", "text/plain"));
      assertThat(builder.response()).isNull();
    }
  }

  @Test
  void text_bodies_pass_through_and_only_null_becomes_empty() {
    assertThat(builder(WebhookHandlerReturnType.text("ok")).getBody()).isEqualTo("ok");
    assertThat(builder(WebhookHandlerReturnType.text(null)).getBody()).isEqualTo("");
    assertThat(builder(WebhookHandlerReturnType.text(0)).getBody()).isEqualTo(0);
    assertThat(builder(WebhookHandlerReturnType.text(false)).getBody()).isEqualTo(false);
    Map<String, Object> object = Map.of("a", 1);
    assertThat(builder(WebhookHandlerReturnType.text(object)).getBody()).isSameAs(object);
    assertThat(builder(WebhookHandlerReturnType.text("ok")).strategy()).isInstanceOf(WebhookTextResponse.class);
  }

  @Test
  void json_bodies_are_stringified_and_falsy_ones_are_empty() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "my_name");
    body.put("missing", null);
    body.put("n", 2.0);
    body.put("list", List.of(1, "a"));

    assertThat(builder(WebhookHandlerReturnType.json(body)).getBody())
        .isEqualTo("{\"name\":\"my_name\",\"missing\":null,\"n\":2,\"list\":[1,\"a\"]}");
    assertThat(builder(WebhookHandlerReturnType.json(List.of())).getBody()).isEqualTo("[]");
    assertThat(builder(WebhookHandlerReturnType.json("text")).getBody()).isEqualTo("\"text\"");
    assertThat(builder(WebhookHandlerReturnType.json(true)).getBody()).isEqualTo("true");
    for (Object falsy : new Object[] {null, false, 0, ""}) {
      assertThat(builder(WebhookHandlerReturnType.json(falsy)).getBody()).as("json %s", falsy).isEqualTo("");
    }
    assertThat(builder(WebhookHandlerReturnType.json(body)).strategy()).isInstanceOf(WebhookJsonResponse.class);
    assertThat(builder(WebhookHandlerReturnType.json(body)).getHeaders())
        .containsExactly(Map.entry("Access-Control-Allow-Origin", "*"), Map.entry("Content-Type", "application/json"));
  }

  @Test
  void file_bodies_pass_through_with_the_attachment_headers() {
    byte[] content = "file content".getBytes(StandardCharsets.UTF_8);
    WebhookResponseBuilder builder = builder(WebhookHandlerReturnType.file(content, "1.txt", "text/plain"));

    assertThat(builder.getBody()).isSameAs(content);
    assertThat(builder.getHeaders()).containsExactly(Map.entry("Access-Control-Allow-Origin", "*"),
        Map.entry("Content-Type", "text/plain"), Map.entry("Content-Disposition", "attachment; filename=1.txt"));
    assertThat(builder.strategy()).isInstanceOf(WebhookFileResponse.class);
  }

  @Test
  void file_names_are_neither_quoted_nor_encoded_and_missing_values_print_like_javascript() {
    assertThat(builder(WebhookHandlerReturnType.file("x", "my report é.pdf", null)).getHeaders())
        .containsEntry("Content-Type", "").containsEntry("Content-Disposition", "attachment; filename=my report é.pdf");
    assertThat(builder(WebhookHandlerReturnType.file("x", null, "")).getHeaders())
        .containsEntry("Content-Type", "").containsEntry("Content-Disposition", "attachment; filename=undefined");
  }

  @Test
  void a_file_result_without_file_fails_when_the_headers_are_built() {
    WebhookResponseBuilder builder = builder(new WebhookHandlerFileReturnType("x", null, null));

    assertThat(builder.getBody()).isEqualTo("x");
    assertThatThrownBy(builder::getHeaders).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot read properties of undefined (reading 'mimeType')");
  }

  @Test
  void handler_headers_override_the_default_and_are_overridden_by_the_type_headers() {
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/xml");
    headers.put("Access-Control-Allow-Origin", "https://a.example");
    headers.put("Test", "test header");
    headers.put("content-type", "kept as another key");
    headers.put("X-Count", 3);
    headers.put("Set-Cookie", List.of("a=1", "b=2"));

    Map<String, Object> merged = builder(new WebhookHandlerTextReturnType("ok", headers)).getHeaders();

    assertThat(merged).containsExactly(
        Map.entry("Access-Control-Allow-Origin", "https://a.example"),
        Map.entry("Content-Type", "text/plain"),
        Map.entry("Test", "test header"),
        Map.entry("content-type", "kept as another key"),
        Map.entry("X-Count", 3),
        Map.entry("Set-Cookie", List.of("a=1", "b=2")));
  }

  @Test
  void the_header_order_is_default_handler_then_type_keys() {
    WebhookResponseBuilder builder = builder(WebhookHandlerReturnType.file("x", "a.csv", "text/csv")
        .withHeaders(Map.of("Test", "1")));

    assertThat(List.copyOf(builder.getHeaders().keySet()))
        .containsExactly("Access-Control-Allow-Origin", "Test", "Content-Type", "Content-Disposition");
  }

  @Test
  void raw_maps_are_read_like_typescript_object_literals() {
    Map<String, Object> text = Map.of("body", "ok", "responseType", "text/plain", "headers", Map.of("Test", "x"));
    Map<String, Object> json = Map.of("body", Map.of("a", 1), "responseType", "application/json");
    Map<String, Object> file = Map.of("body", "c", "responseType", "file",
        "file", Map.of("name", "1.txt", "mimeType", "text/plain"));

    assertThat(builder(text).response()).isEqualTo(new WebhookHandlerTextReturnType("ok", Map.of("Test", "x")));
    assertThat(builder(text).getHeaders()).containsEntry("Test", "x");
    assertThat(builder(json).response()).isEqualTo(new WebhookHandlerJsonReturnType(Map.of("a", 1), null));
    assertThat(builder(json).getBody()).isEqualTo("{\"a\":1}");
    assertThat(builder(file).getHeaders()).containsEntry("Content-Disposition", "attachment; filename=1.txt");
    assertThat(logged).isEmpty();
  }

  @Test
  void an_unknown_response_type_is_sent_as_a_file_and_logged() {
    Map<String, Object> withFile = Map.of("body", "c", "responseType", "text/html",
        "file", Map.of("name", "page.html", "mimeType", "text/html"));
    Map<String, Object> withoutType = Map.of("body", "c");

    WebhookResponseBuilder builder = builder(withFile);

    assertThat(builder.strategy()).isInstanceOf(WebhookFileResponse.class);
    assertThat(builder.getHeaders()).containsEntry("Content-Type", "text/html");
    assertThat(logged).containsExactly("Unexpected response type. Using File");
    assertThatThrownBy(() -> builder(withoutType).getHeaders()).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot read properties of undefined (reading 'mimeType')");
    assertThatThrownBy(() -> builder(new Object()).getHeaders()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void the_default_headers_are_access_control_allow_origin_star_and_read_only() {
    assertThat(WebhookResponseBuilder.DEFAULT_HEADERS).containsExactly(Map.entry("Access-Control-Allow-Origin", "*"));
    assertThatThrownBy(() -> WebhookResponseBuilder.DEFAULT_HEADERS.put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
