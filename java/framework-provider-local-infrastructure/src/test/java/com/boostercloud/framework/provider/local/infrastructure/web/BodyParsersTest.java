package com.boostercloud.framework.provider.local.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

/** {@code body-parser} 1.20 behaviour of {@link BodyParsers}. */
class BodyParsersTest {

  private final AtomicReference<Throwable> error = new AtomicReference<>();
  private boolean nextCalled;

  private HttpRequest run(RequestHandler parser, HttpRequest request) {
    parser.handle(request, new MemoryHttpResponse(), failure -> {
      nextCalled = true;
      error.set(failure);
    });
    return request;
  }

  private static HttpRequest json(String body) {
    return HttpRequest.builder("POST", "/").body("application/json", body).build();
  }

  @Test
  void parsesJsonObjectsAndArrays() {
    assertThat(run(BodyParsers.json(), json("{\"a\":1,\"b\":[true,null]}")).body())
        .isEqualTo(Map.of("a", 1, "b", java.util.Arrays.asList(true, null)));
    assertThat(run(BodyParsers.json(), json(" [1,2] ")).body()).isEqualTo(List.of(1, 2));
    assertThat(nextCalled).isTrue();
    assertThat(error.get()).isNull();
  }

  @Test
  void anEmptyJsonBodyIsAnEmptyObject() {
    assertThat(run(BodyParsers.json(), json("")).body()).isEqualTo(Map.of());
  }

  @Test
  void requestsWithoutBodyOrOfAnotherTypeGetAnEmptyObject() {
    HttpRequest noBody = run(BodyParsers.json(), HttpRequest.builder("GET", "/").build());
    assertThat(noBody.body()).isEqualTo(Map.of());
    assertThat(noBody.isBodyRead()).isFalse();
    HttpRequest text = run(BodyParsers.json(), HttpRequest.builder("POST", "/").body("text/plain", "{}").build());
    assertThat(text.body()).isEqualTo(Map.of());
    assertThat(text.isBodyRead()).isFalse();
    assertThat(new String(text.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("{}");
  }

  @Test
  void theContentTypeMayHaveParametersAndAnyCase() {
    HttpRequest request = HttpRequest.builder("POST", "/")
        .body("Application/JSON; charset=UTF-8", "{\"x\":\"é\"}").build();
    assertThat(run(BodyParsers.json(), request).body()).isEqualTo(Map.of("x", "é"));
  }

  @Test
  void vendorJsonTypesAreNotParsed() {
    HttpRequest request = HttpRequest.builder("POST", "/").body("application/vnd.api+json", "{}").build();
    run(BodyParsers.json(), request);
    assertThat(request.isBodyRead()).isFalse();
  }

  @Test
  void theVerifierReceivesTheRawBytes() {
    AtomicReference<byte[]> raw = new AtomicReference<>();
    HttpRequest request = run(BodyParsers.json(BodyParsers.DEFAULT_LIMIT, (req, res, bytes) -> {
      raw.set(bytes);
      req.rawBody(bytes);
    }), json("{\"a\":1}"));
    assertThat(new String(raw.get(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
    assertThat(request.rawBody()).isEqualTo(raw.get());
    assertThat(request.readAllBytes()).isEmpty();
  }

  @Test
  void strictModeRejectsTopLevelPrimitives() {
    run(BodyParsers.json(), json("\"abc\""));
    assertThat(error.get()).isInstanceOf(SyntaxError.class)
        .hasMessage("Unexpected token '\"', \"\"abc\"\" is not valid JSON");
    assertThat(((HttpStatusError) error.get()).status()).isEqualTo(400);
  }

  @Test
  void malformedJsonIsASyntaxError() {
    run(BodyParsers.json(), json("{\"a\":"));
    assertThat(error.get()).isInstanceOf(SyntaxError.class).hasMessage("Unexpected end of JSON input");
    run(BodyParsers.json(), json("{\"a\" 1}"));
    assertThat(error.get()).isInstanceOf(SyntaxError.class).hasMessageStartingWith("Unexpected token ");
    run(BodyParsers.json(), json("{} x"));
    assertThat(error.get()).isInstanceOf(SyntaxError.class);
    run(BodyParsers.json(), json("   "));
    assertThat(error.get()).isInstanceOf(SyntaxError.class).hasMessage("Unexpected end of JSON input");
  }

  @Test
  void bodiesOverTheLimitAreRejected() {
    run(BodyParsers.json(10, null), json("{\"a\":\"0123456789\"}"));
    assertThat(error.get()).isInstanceOf(PayloadTooLargeError.class).hasMessage("request entity too large");
    assertThat(((PayloadTooLargeError) error.get()).type()).isEqualTo("entity.too.large");

    HttpRequest chunked = HttpRequest.builder("POST", "/").header("content-type", "application/json")
        .header("transfer-encoding", "chunked")
        .body(new java.io.ByteArrayInputStream("{\"a\":\"0123456789\"}".getBytes(StandardCharsets.UTF_8))).build();
    error.set(null);
    run(BodyParsers.json(10, null), chunked);
    assertThat(error.get()).isInstanceOf(PayloadTooLargeError.class);
  }

  @Test
  void unsupportedCharsetsAreRejected() {
    HttpRequest request = HttpRequest.builder("POST", "/").body("application/json; charset=latin1", "{}").build();
    run(BodyParsers.json(), request);
    assertThat(error.get()).isInstanceOf(UnsupportedMediaTypeError.class).hasMessage("unsupported charset \"LATIN1\"");
    assertThat(((HttpStatusError) error.get()).status()).isEqualTo(415);
  }

  @Test
  void gzipBodiesAreInflatedAndUnknownEncodingsRejected() throws IOException {
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      gzip.write("{\"zipped\":true}".getBytes(StandardCharsets.UTF_8));
    }
    HttpRequest request = HttpRequest.builder("POST", "/").header("content-type", "application/json")
        .header("content-encoding", "gzip").body(compressed.toByteArray()).build();
    assertThat(run(BodyParsers.json(), request).body()).isEqualTo(Map.of("zipped", true));

    HttpRequest brotli = HttpRequest.builder("POST", "/").header("content-type", "application/json")
        .header("content-encoding", "br").body(new byte[] {1, 2}).build();
    run(BodyParsers.json(), brotli);
    assertThat(error.get()).isInstanceOf(UnsupportedMediaTypeError.class)
        .hasMessage("unsupported content encoding \"br\"");
  }

  @Test
  void anAlreadyParsedBodyIsLeftAlone() {
    HttpRequest request = json("{\"a\":1}");
    run(BodyParsers.json(), request);
    run(BodyParsers.urlencoded(), request);
    run(BodyParsers.raw(), request);
    assertThat(request.body()).isEqualTo(Map.of("a", 1));
  }

  @Test
  void parsesExtendedUrlencodedForms() {
    HttpRequest request = HttpRequest.builder("POST", "/")
        .body("application/x-www-form-urlencoded", "name=John+Doe&tags[]=a&tags[]=b&address[city]=Madrid").build();
    assertThat(run(BodyParsers.urlencoded(), request).body()).isEqualTo(Map.of("name", "John Doe",
        "tags", List.of("a", "b"), "address", Map.of("city", "Madrid")));
    assertThat(request.rawBody()).isNull();
  }

  @Test
  void urlencodedOnlyAcceptsUtf8AndAThousandParameters() {
    HttpRequest latin = HttpRequest.builder("POST", "/")
        .body("application/x-www-form-urlencoded; charset=utf-16", "a=1").build();
    run(BodyParsers.urlencoded(), latin);
    assertThat(error.get()).isInstanceOf(UnsupportedMediaTypeError.class).hasMessage("unsupported charset \"UTF-16\"");

    HttpRequest many = HttpRequest.builder("POST", "/")
        .body("application/x-www-form-urlencoded", "a=1&".repeat(1001)).build();
    run(BodyParsers.urlencoded(BodyParsers.parseBytes("6mb")), many);
    assertThat(error.get()).isInstanceOf(PayloadTooLargeError.class).hasMessage("too many parameters");
  }

  @Test
  void rawReadsOctetStreams() {
    HttpRequest request = HttpRequest.builder("POST", "/").header("content-type", "application/octet-stream")
        .body(new byte[] {1, 2, 3}).build();
    assertThat((byte[]) run(BodyParsers.raw(), request).body()).containsExactly(1, 2, 3);
  }

  @Test
  void parsesSizes() {
    assertThat(BodyParsers.parseBytes("6mb")).isEqualTo(6291456);
    assertThat(BodyParsers.parseBytes("100kb")).isEqualTo(102400);
    assertThat(BodyParsers.parseBytes("1.5KB")).isEqualTo(1536);
    assertThat(BodyParsers.parseBytes("42")).isEqualTo(42);
  }
}
