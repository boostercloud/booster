package com.boostercloud.framework.provider.local.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.types.Logger;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link Uris}, {@link MimeTypes}, {@link Cors}, {@link FinalHandler} and {@link HttpRequest} details. */
class WebHelpersTest {

  @Test
  void decodeUriComponentIsStrict() {
    assertThat(Uris.decodeURIComponent("a%20b+c%C3%B1")).isEqualTo("a b+cñ");
    assertThatThrownBy(() -> Uris.decodeURIComponent("%E0%A4%A")).isInstanceOf(URIError.class)
        .hasMessage("URI malformed");
    assertThatThrownBy(() -> Uris.decodeURIComponent("%FF")).isInstanceOf(URIError.class);
    assertThat(Uris.decodeLenient("%zz")).isEqualTo("%zz");
  }

  @Test
  void encodeUrlKeepsValidEscapes() {
    assertThat(Uris.encodeUrl("/a b/%20/ñ/<x>")).isEqualTo("/a%20b/%20/%C3%B1/%3Cx%3E");
    assertThat(Uris.escapeHtml("<a href=\"x\">'&'</a>")).isEqualTo("&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;");
  }

  @Test
  void mimeTypeHelpers() {
    assertThat(MimeTypes.is("application/json; charset=utf-8", "application/json")).isTrue();
    assertThat(MimeTypes.is("text/plain", "text/*")).isTrue();
    assertThat(MimeTypes.is("not a type", "application/json")).isFalse();
    assertThat(MimeTypes.charsetOf("text/plain; Charset=\"UTF-8\"")).isEqualTo("utf-8");
    assertThat(MimeTypes.setCharset("text/plain; format=flowed", "utf-8")).isEqualTo("text/plain; charset=utf-8; format=flowed");
    assertThat(MimeTypes.lookup("html")).isEqualTo("text/html");
    assertThat(MimeTypes.lookup("unknown-ext")).isEqualTo("application/octet-stream");
    assertThat(MimeTypes.forFileName("a.png")).isEqualTo("image/png");
    assertThat(MimeTypes.forFileName("README")).isEqualTo("application/octet-stream");
  }

  @Test
  void corsAnswersPreflightRequests() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    boolean[] next = {false};
    Cors.middleware().handle(HttpRequest.builder("OPTIONS", "/graphql")
        .header("Access-Control-Request-Headers", "content-type,authorization").build(), response, e -> next[0] = true);

    assertThat(next[0]).isFalse();
    assertThat(response.sentStatus()).isEqualTo(204);
    assertThat(response.headers()).containsEntry("Access-Control-Allow-Origin", java.util.List.of("*"))
        .containsEntry("Access-Control-Allow-Methods", java.util.List.of("GET,HEAD,PUT,PATCH,POST,DELETE"))
        .containsEntry("Access-Control-Allow-Headers", java.util.List.of("content-type,authorization"))
        .containsEntry("Vary", java.util.List.of("Access-Control-Request-Headers"))
        .containsEntry("Content-Length", java.util.List.of("0"));
  }

  @Test
  void corsAddsTheOriginHeaderToOtherRequests() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    boolean[] next = {false};
    Cors.middleware().handle(HttpRequest.builder("POST", "/graphql").build(), response, e -> next[0] = true);
    assertThat(next[0]).isTrue();
    assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("*");
    assertThat(response.headersSent()).isFalse();
  }

  @Test
  void finalHandlerAnswersExpressNotFoundPage() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    FinalHandler.Outcome outcome = FinalHandler.handle(null,
        HttpRequest.builder("POST", "/unknown/<x>?q=1").build(), response, Logger.NOOP);

    assertThat(outcome).isEqualTo(FinalHandler.Outcome.RESPONDED);
    assertThat(response.sentStatus()).isEqualTo(404);
    assertThat(response.bodyAsString()).isEqualTo("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
        + "<title>Error</title>\n</head>\n<body>\n<pre>Cannot POST /unknown/%3Cx%3E</pre>\n</body>\n</html>\n");
    assertThat(response.getHeader("Content-Type")).isEqualTo("text/html; charset=utf-8");
    assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'");
    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  @Test
  void finalHandlerLogsErrorsAndUsesTheirStatus() {
    Logger logger = mock(Logger.class);
    MemoryHttpResponse response = new MemoryHttpResponse();
    PayloadTooLargeError error = new PayloadTooLargeError("request entity too large", "entity.too.large");

    FinalHandler.handle(error, HttpRequest.builder("POST", "/").build(), response, logger);

    verify(logger).error(error);
    assertThat(response.sentStatus()).isEqualTo(413);
    assertThat(response.bodyAsString()).contains("<pre>Payload Too Large</pre>");
  }

  @Test
  void finalHandlerCannotAnswerTwice() {
    MemoryHttpResponse sent = new MemoryHttpResponse();
    sent.send("done");
    assertThat(FinalHandler.handle(new IllegalStateException("late"), HttpRequest.builder("GET", "/").build(), sent,
        Logger.NOOP)).isEqualTo(FinalHandler.Outcome.ALREADY_SENT);
    MemoryHttpResponse streaming = new MemoryHttpResponse();
    streaming.outputStream();
    try {
      streaming.outputStream().write(1);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
    assertThat(FinalHandler.handle(new IllegalStateException("late"), HttpRequest.builder("GET", "/").build(),
        streaming, Logger.NOOP)).isEqualTo(FinalHandler.Outcome.ABORT);
  }

  @Test
  void requestHeadersFollowNodesJoiningRules() {
    HttpRequest request = HttpRequest.builder("get", "/x?a=1")
        .header("Accept", "text/html").header("accept", "application/json")
        .header("Cookie", "a=1").header("cookie", "b=2")
        .header("Content-Type", "application/json").header("content-type", "text/plain")
        .build();
    assertThat(request.method()).isEqualTo("GET");
    assertThat(request.headers()).isEqualTo(Map.of("accept", "text/html, application/json", "cookie", "a=1; b=2",
        "content-type", "application/json"));
    assertThat(request.path()).isEqualTo("/x");
    assertThat(request.hasBody()).isFalse();
    assertThat(request.toLocalHttpRequest().url()).isEqualTo("/x?a=1");
  }
}
