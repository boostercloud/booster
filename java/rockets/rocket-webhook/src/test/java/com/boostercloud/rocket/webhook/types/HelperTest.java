package com.boostercloud.rocket.webhook.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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

  private static String routeWithHost(String url, String host) {
    WebhookHttpRequest.Builder builder = WebhookHttpRequest.builder("POST", url);
    if (host != null) {
      builder.header("host", host);
    }
    return Helper.getRouteFromRequest(WebhookRequest.of(builder.build()));
  }

  /**
   * {@code new URL('/test?x=1', 'http://' + host + '/')} with Node 24: {@code "/test"} when it parses, {@code null}
   * when it throws {@code TypeError: Invalid URL}.
   */
  static Stream<Arguments> hostHeaders() {
    return Stream.of(
        Arguments.of("localhost:3000", "/test"),
        Arguments.of("localhost", "/test"),
        Arguments.of("127.0.0.1:8080", "/test"),
        Arguments.of("[::1]:3000", "/test"),
        Arguments.of("[::1]", "/test"),
        Arguments.of("[2001:db8::1]", "/test"),
        Arguments.of("[::ffff:192.168.0.1]", "/test"),
        Arguments.of("[1:2:3:4:5:6:7:8]", "/test"),
        Arguments.of("[1:2:3:4:5:6:7:8:9]", null),
        Arguments.of("[::1::2]", null),
        Arguments.of("[:1]", null),
        Arguments.of("[1::2::]", null),
        Arguments.of("[::1", null),
        Arguments.of("[]", null),
        Arguments.of("[x]", null),
        Arguments.of("[::1.2.3]", null),
        Arguments.of("[::1.2.3.4]", "/test"),
        Arguments.of("[::1.2.3.256]", null),
        Arguments.of("[::01.2.3.4]", null),
        Arguments.of("[1:2:3:4:5:6:7::]", "/test"),
        Arguments.of("[1:2:3:4:5:6::1.2.3.4]", null),
        Arguments.of("[1:2:3:4:5:6:7:1.2.3.4]", null),
        Arguments.of("[12345::]", null),
        Arguments.of("[::1:]", null),
        Arguments.of("[::1]x", null),
        Arguments.of("", null),
        Arguments.of(" ", null),
        Arguments.of("a b", null),
        Arguments.of(" a", null),
        Arguments.of("a:b", null),
        Arguments.of("h:99999", null),
        Arguments.of("h:65535", "/test"),
        Arguments.of("h:65536", null),
        Arguments.of("h:0080", "/test"),
        Arguments.of("h:", "/test"),
        Arguments.of("h:1:2", null),
        Arguments.of(":80", null),
        Arguments.of("user@h", "/test"),
        Arguments.of("@h", "/test"),
        Arguments.of("h@", null),
        Arguments.of("a@b@c", "/test"),
        Arguments.of("user:pass@h:1", "/test"),
        Arguments.of("a%20b", null),
        Arguments.of("%41.com", "/test"),
        Arguments.of("a%2Fb", null),
        Arguments.of("a%zz", null),
        Arguments.of("%", null),
        Arguments.of("a<b", null),
        Arguments.of("a>b", null),
        Arguments.of("a^b", null),
        Arguments.of("a|b", null),
        Arguments.of("a]b", null),
        Arguments.of("a[b", null),
        Arguments.of("a_b", "/test"),
        Arguments.of("a/b", "/test"),
        Arguments.of("a?b", "/test"),
        Arguments.of("a#b", "/test"),
        Arguments.of("a\\b", "/test"),
        Arguments.of("/a", "/test"),
        Arguments.of("//a", "/test"),
        Arguments.of("/", null),
        Arguments.of("A.COM", "/test"),
        Arguments.of("a..b", "/test"),
        Arguments.of(".", "/test"),
        Arguments.of("..", "/test"),
        Arguments.of("a.", "/test"),
        Arguments.of("-a.com", "/test"),
        Arguments.of("a\u0001b", null),
        Arguments.of("a\u007fb", null),
        Arguments.of("a\tb", "/test"),
        Arguments.of("a\nb", "/test"),
        Arguments.of("a%00b", null),
        Arguments.of("a%7Fb", null),
        Arguments.of("1.2.3.4", "/test"),
        Arguments.of("1.2.3.256", null),
        Arguments.of("1.2.3.4.5", null),
        Arguments.of("0x7f.1", "/test"),
        Arguments.of("0x", "/test"),
        Arguments.of("0xg", "/test"),
        Arguments.of("09", null),
        Arguments.of("08.1", null),
        Arguments.of("1.2.3.4.", "/test"),
        Arguments.of("1.2.3.4..", "/test"),
        Arguments.of("999999999999", null),
        Arguments.of("4294967295", "/test"),
        Arguments.of("4294967296", null),
        Arguments.of("1.0x100", "/test"),
        Arguments.of("a.1", null),
        Arguments.of("a.0x1", null),
        Arguments.of("1.a", "/test"),
        Arguments.of("1.2.3.08", null),
        Arguments.of("0x100000000", null),
        Arguments.of("a,b", "/test"),
        Arguments.of("a;b", "/test"),
        Arguments.of("a=b", "/test"),
        Arguments.of("a'b", "/test"),
        Arguments.of("a\"b", "/test"),
        Arguments.of("a{b", "/test"),
        Arguments.of("a`b", "/test"),
        Arguments.of("a!b", "/test"),
        Arguments.of("a*b", "/test"),
        Arguments.of("a(b)", "/test"),
        Arguments.of("a~b", "/test"),
        Arguments.of("a$b", "/test"),
        Arguments.of("a&b", "/test"),
        Arguments.of("a+b", "/test"),
        Arguments.of("xn--ls8h", "/test"),
        Arguments.of("xn--zz", null),
        Arguments.of("xn--abc", null),
        Arguments.of("xn--", null),
        Arguments.of("XN--LS8H", "/test"),
        Arguments.of("xn--abc-", null),
        Arguments.of("xn--a.com", null),
        Arguments.of("xn--4ca", "/test"),
        Arguments.of("xn--exmple-cua.com", "/test"),
        Arguments.of("exämple.com", "/test"),
        Arguments.of("EXÄMPLE.com", "/test"),
        Arguments.of("café", "/test"),
        Arguments.of("­", null),
        Arguments.of("名.com", "/test"),
        Arguments.of("%E5%90%8D.com", "/test"),
        Arguments.of("%FF", null),
        Arguments.of("�", null),
        Arguments.of("a。b", "/test"),
        Arguments.of("ää", "/test"));
  }

  @ParameterizedTest(name = "Host {0} -> {1}")
  @MethodSource("hostHeaders")
  void the_base_built_from_the_host_header_must_parse_as_with_new_url(String host, String pathname) {
    if (pathname == null) {
      assertThatThrownBy(() -> routeWithHost("/test?x=1", host)).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid URL");
    } else {
      assertThat(routeWithHost("/test?x=1", host)).isEqualTo("test");
    }
  }

  @Test
  void a_missing_host_header_is_the_valid_base_http_undefined() {
    assertThat(routeWithHost("/test", null)).isEqualTo("test");
    assertThat(Helper.isValidBase("undefined")).isTrue();
  }

  /**
   * {@code new URL(url, 'http://localhost:3000/').pathname} with Node 24, then the route ({@code null}: {@code TypeError:
   * Invalid URL}).
   */
  static Stream<Arguments> absoluteUrls() {
    return Stream.of(
        Arguments.of("https://h/api/test", "test"),
        Arguments.of("http://a b/test", null),
        Arguments.of("https://[bad/test", null),
        Arguments.of("http://h:x/api/t", null),
        Arguments.of("//h/api/x", "x"),
        Arguments.of("//a b/x", null),
        Arguments.of("https:h/api/x", "x"),
        Arguments.of("ws://a%zz/x", null),
        Arguments.of("http://h:99999/x", null),
        Arguments.of("http:///x", ""),
        Arguments.of("https://@/x", null),
        Arguments.of("https://user@/x", null),
        Arguments.of("HTTP://H/API/T", "API/T"),
        Arguments.of("https://h:443/api/test?x#y", "test"),
        Arguments.of("wss://[::1]:8/a/b", "a/b"),
        Arguments.of("ftp://h<x/y", null),
        Arguments.of("https://xn--zz/x", null),
        Arguments.of("https://exämple.com/api/x", "x"),
        // The base's scheme with one slash is a path relative to the base; other special schemes take a host
        Arguments.of("http:/h/api/x", "h/api/x"),
        Arguments.of("https:/h/api/x", "x"),
        Arguments.of("\\\\h\\api\\x", "x"));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("absoluteUrls")
  void absolute_and_scheme_relative_urls_need_a_valid_authority(String url, String expected) {
    if (expected == null) {
      assertThatThrownBy(() -> route(url)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid URL");
    } else {
      assertThat(route(url)).isEqualTo(expected);
    }
  }

  @Test
  void punycode_decodes_rfc_3492_labels() {
    assertThat(WhatwgHost.punycodeDecode("ls8h")).isEqualTo("💩");
    assertThat(WhatwgHost.punycodeDecode("4ca")).isEqualTo("ä");
    assertThat(WhatwgHost.punycodeDecode("exmple-cua")).isEqualTo("exämple");
    assertThat(WhatwgHost.punycodeDecode("zz")).isNull();
    assertThat(WhatwgHost.punycodeDecode("-abc")).isNull();
  }

  @Test
  void a_request_without_req_fails_like_typescript() {
    assertThatThrownBy(() -> Helper.getRouteFromRequest(WebhookRequest.from(Map.of("x", 1))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot read properties of undefined (reading 'url')");
  }
}
