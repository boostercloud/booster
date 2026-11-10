package com.boostercloud.rocket.webhook.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * {@code Helper.getRouteFromRequest}: the WHATWG pathname without a leading {@code /api/} or {@code /}. The expected
 * values were computed with Node 24 ({@code new URL(url, 'http://localhost:3000/').pathname.replace(...)}).
 */
class HelperTest {

  private static String route(String url) {
    return Helper.getRouteFromRequest(WebhookRequest.of(
        WebhookHttpRequest.builder("POST", url).header("host", "localhost:3000").build()));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(delimiter = '|', value = {
      "/test|test",
      "/test?x=1|test",
      "/test#fragment|test",
      "/api/test|test",
      "/api/clients/other?param1=testvalue|clients/other",
      "https://myappdevrgwebhookfunc.azurewebsites.net/api/clients/other?x=1|clients/other",
      "http://other.host:8080/test|test",
      "/clients/other|clients/other",
      "/test/|test/",
      "/Test|Test",
      "/api|api",
      "/api/api/foo|api/foo",
      "/a/./b/../c|a/c",
      "/a%20b|a%20b",
      "/a b|a%20b",
      "/café|caf%C3%A9",
      "//host/test|test",
      "test|test",
      "\\\\test|''",
      "/a\\b|a/b",
      "?only=query|''",
  })
  void extracts_the_route(String url, String expected) {
    assertThat(route(url)).isEqualTo(expected.equals("''") ? "" : expected);
  }

  @Test
  void a_missing_url_is_the_text_undefined() {
    assertThat(route(null)).isEqualTo("undefined");
  }

  @Test
  void a_request_without_req_fails_like_typescript() {
    assertThatThrownBy(() -> Helper.getRouteFromRequest(WebhookRequest.from(Map.of("x", 1))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot read properties of undefined (reading 'url')");
  }
}
