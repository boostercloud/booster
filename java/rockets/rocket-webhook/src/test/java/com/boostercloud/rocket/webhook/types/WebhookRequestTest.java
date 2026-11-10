package com.boostercloud.rocket.webhook.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** The raw rocket requests of both transports, read by {@link WebhookRequest#from}. */
class WebhookRequestTest {

  @Test
  void reads_the_local_shape() {
    WebhookHttpRequest req = WebhookHttpRequest.builder("POST", "/test?a=1").header("Content-Type", "text/plain")
        .build();
    WebhookRequest request = WebhookRequest.from(Map.of("BOOSTER_ROCKET_FUNCTION_ID", "rocket-webhook", "req", req));
    assertThat(request.req()).isSameAs(req);
    assertThat(request.properties()).containsExactly(Map.entry("BOOSTER_ROCKET_FUNCTION_ID", "rocket-webhook"));
    assertThat(WebhookRequest.from(request)).isSameAs(request);
  }

  @Test
  void reads_the_azure_shape() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("method", "GET");
    raw.put("url", "https://app.azurewebsites.net/api/test?a=1&a=2");
    raw.put("originalUrl", "https://app.azurewebsites.net/api/test?a=1&a=2");
    raw.put("headers", Map.of("content-type", "application/json", "x-count", 3));
    raw.put("query", Map.of("a", "2"));
    raw.put("params", Map.of("route", "test"));
    raw.put("rawBody", "{\"a\":1}");
    raw.put("body", Map.of("a", 1));
    WebhookHttpRequest req = WebhookRequest.from(Map.of("req", raw)).req();
    assertThat(req.method()).isEqualTo("GET");
    assertThat(req.url()).isEqualTo("https://app.azurewebsites.net/api/test?a=1&a=2");
    assertThat(req.originalUrl()).isEqualTo(req.url());
    assertThat(req.headers()).containsEntry("content-type", "application/json").containsEntry("x-count", "3");
    assertThat(req.query()).containsExactly(Map.entry("a", "2"));
    assertThat(req.params()).containsExactly(Map.entry("route", "test"));
    assertThat(req.rawBody()).isEqualTo("{\"a\":1}");
    assertThat(req.body()).isEqualTo(Map.of("a", 1));
    assertThat(req.bodyStream()).isNull();
  }

  @Test
  void anything_else_has_no_req_and_null_fails() {
    assertThat(WebhookRequest.from("text").req()).isNull();
    assertThat(WebhookRequest.from(Map.of("req", 42)).req()).isNull();
    assertThatThrownBy(() -> WebhookRequest.from(null)).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot read properties of undefined (reading 'req')");
  }

  @Test
  void header_names_are_lower_cased_and_maps_never_null() throws IOException {
    InputStream stream = new ByteArrayInputStream("body".getBytes(StandardCharsets.UTF_8));
    Supplier<InputStream> supplier = () -> stream;
    WebhookHttpRequest req = WebhookHttpRequest.builder("POST", "/x").headers(Map.of("X-Custom", "1"))
        .bodyStream(supplier).build();
    assertThat(req.headers()).containsExactly(Map.entry("x-custom", "1"));
    assertThat(req.header("X-CUSTOM")).isEqualTo("1");
    assertThat(req.query()).isEmpty();
    assertThat(req.params()).isEmpty();
    assertThat(req.originalUrl()).isEqualTo("/x");
    assertThat(req.bodyStream().get().readAllBytes()).asString(StandardCharsets.UTF_8).isEqualTo("body");
    assertThat(new WebhookHttpRequest("GET", "/", null, null, null, null, null, null, null).headers()).isEmpty();
  }

  @Test
  void the_event_gives_the_raw_body_as_bytes_or_text() {
    WebhookEvent bytes = new WebhookEvent(null, "r", "POST", "/r", "/r", Map.of("content-type", "application/json"),
        Map.of(), Map.of(), "{}".getBytes(StandardCharsets.UTF_8), Map.of(), null);
    assertThat(bytes.rawBodyText()).isEqualTo("{}");
    assertThat(bytes.rawBodyBytes()).isEqualTo("{}".getBytes(StandardCharsets.UTF_8));
    assertThat(bytes.header("Content-Type")).isEqualTo("application/json");
    WebhookEvent text = new WebhookEvent(null, "r", "POST", "/r", "/r", Map.of(), Map.of(), Map.of(), "x", "x", null);
    assertThat(text.rawBodyBytes()).isEqualTo(new byte[] {'x'});
    WebhookEvent none = new WebhookEvent(null, "r", "POST", "/r", "/r", null, Map.of(), Map.of(), null, null, null);
    assertThat(none.rawBodyText()).isNull();
    assertThat(none.rawBodyBytes()).isNull();
    assertThat(none.header("x")).isNull();
  }

  @Test
  void parsed_files_compare_by_content() {
    WebhookParsedFile file = new WebhookParsedFile("f", new byte[] {1, 2}, "a.bin", "7bit", "application/octet-stream");
    assertThat(file).isEqualTo(new WebhookParsedFile("f", new byte[] {1, 2}, "a.bin", "7bit",
        "application/octet-stream"));
    assertThat(file).hasSameHashCodeAs(new WebhookParsedFile("f", new byte[] {1, 2}, "a.bin", "7bit",
        "application/octet-stream"));
    assertThat(file.size()).isEqualTo(2);
    assertThat(file.toString()).contains("<2 bytes>");
    byte[] content = file.bufferFile();
    content[0] = 9;
    assertThat(file.bufferFile()[0]).isEqualTo((byte) 1);
    WebhookMultipartForm form = new WebhookMultipartForm(null, List.of(file));
    assertThat(form.fields()).isEmpty();
    assertThat(form.files()).containsExactly(file);
  }
}
