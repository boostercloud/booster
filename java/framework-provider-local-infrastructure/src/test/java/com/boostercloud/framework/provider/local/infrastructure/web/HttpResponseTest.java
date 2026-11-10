package com.boostercloud.framework.provider.local.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Express {@code res.json/send/download} semantics of {@link HttpResponse}. */
class HttpResponseTest {

  @TempDir
  Path directory;

  @Test
  void jsonSendsJsonStringifyWithUtf8() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    response.status(201).json(Map.of("a", 1.0));
    assertThat(response.sentStatus()).isEqualTo(201);
    assertThat(response.bodyAsString()).isEqualTo("{\"a\":1}");
    assertThat(response.getHeader("content-type")).isEqualTo("application/json; charset=utf-8");
    assertThat(response.getHeader("Content-Length")).isEqualTo("7");
    assertThat(response.finished()).isTrue();
  }

  @Test
  void jsonOfNullIsNull() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    response.json(null);
    assertThat(response.bodyAsString()).isEqualTo("null");
  }

  @Test
  void sendDefaultsStringsToHtmlAndBytesToOctetStream() {
    MemoryHttpResponse text = new MemoryHttpResponse();
    text.send("<p>hi</p>");
    assertThat(text.getHeader("Content-Type")).isEqualTo("text/html; charset=utf-8");

    MemoryHttpResponse typed = new MemoryHttpResponse();
    typed.setHeader("Content-Type", "text/plain; charset=latin1").send("ñ");
    assertThat(typed.getHeader("Content-Type")).isEqualTo("text/plain; charset=utf-8");
    assertThat(typed.bodyBytes()).isEqualTo("ñ".getBytes(StandardCharsets.UTF_8));

    MemoryHttpResponse bytes = new MemoryHttpResponse();
    bytes.send(new byte[] {7});
    assertThat(bytes.getHeader("Content-Type")).isEqualTo("application/octet-stream");

    MemoryHttpResponse object = new MemoryHttpResponse();
    object.send(List.of(1, 2));
    assertThat(object.bodyAsString()).isEqualTo("[1,2]");
    assertThat(object.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
  }

  @Test
  void noContentResponsesDropTheBody() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    response.status(204).send("ignored");
    assertThat(response.bodyBytes()).isEmpty();
    assertThat(response.getHeader("Content-Type")).isNull();
    assertThat(response.getHeader("Content-Length")).isNull();
  }

  @Test
  void headResponsesKeepTheHeadersWithoutTheBody() {
    MemoryHttpResponse response = new MemoryHttpResponse(true);
    response.json(Map.of("a", "b"));
    assertThat(response.bodyBytes()).isEmpty();
    assertThat(response.getHeader("Content-Length")).isEqualTo("9");
  }

  @Test
  void headerAddsTheDefaultCharsetButSetHeaderDoesNot() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    response.header("Content-Type", "application/json");
    assertThat(response.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
    response.header("Content-Type", "image/png");
    assertThat(response.getHeader("Content-Type")).isEqualTo("image/png");
    response.setHeader("Content-Type", "text/plain");
    assertThat(response.getHeader("Content-Type")).isEqualTo("text/plain");
    response.type("json");
    assertThat(response.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
  }

  @Test
  void headersCannotChangeOnceSent() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    response.send("x");
    assertThatThrownBy(() -> response.setHeader("X-A", "b"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot set headers after they are sent to the client");
    assertThatThrownBy(() -> response.send("again")).isInstanceOf(IllegalStateException.class);
    assertThat(response.headersSent()).isTrue();
  }

  @Test
  void invalidStatusCodesAreRejected() {
    assertThatThrownBy(() -> new MemoryHttpResponse().status(42)).hasMessage("Invalid status code: 42");
  }

  @Test
  void endSendsAnEmptyBody() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    response.end();
    response.end();
    assertThat(response.sentStatus()).isEqualTo(200);
    assertThat(response.getHeader("Content-Length")).isEqualTo("0");
  }

  @Test
  void streamsCommitOnFirstWriteAndFinishOnClose() throws IOException {
    MemoryHttpResponse response = new MemoryHttpResponse();
    response.setHeader("X-Stream", "yes");
    OutputStream out = response.outputStream();
    assertThat(response.headersSent()).isFalse();
    out.write("hello ".getBytes(StandardCharsets.UTF_8));
    assertThat(response.headersSent()).isTrue();
    assertThat(response.finished()).isFalse();
    out.write("world".getBytes(StandardCharsets.UTF_8));
    response.end();
    assertThat(response.finished()).isTrue();
    assertThat(response.bodyAsString()).isEqualTo("hello world");
    assertThat(response.sentHeaders()).contains(Map.entry("X-Stream", "yes"));
  }

  @Test
  void varyAppendsFields() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    response.vary("Origin").vary("Accept").vary("origin");
    assertThat(response.getHeader("Vary")).isEqualTo("Origin, Accept");
  }

  @Test
  void downloadSendsTheFileAsAnAttachment() throws IOException {
    Path file = Files.writeString(directory.resolve("notes.txt"), "file content");
    MemoryHttpResponse response = new MemoryHttpResponse();

    response.download(file);

    assertThat(response.bodyAsString()).isEqualTo("file content");
    assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"notes.txt\"");
    assertThat(response.getHeader("Content-Type")).isEqualTo("text/plain; charset=UTF-8");
    assertThat(response.getHeader("Content-Length")).isEqualTo("12");
    assertThat(response.getHeader("Cache-Control")).isEqualTo("public, max-age=0");
    assertThat(response.getHeader("Accept-Ranges")).isEqualTo("bytes");
    assertThat(response.getHeader("ETag")).startsWith("W/\"c-");
    assertThat(response.getHeader("Last-Modified")).endsWith(" GMT");
  }

  @Test
  void downloadOfAMissingFileFailsWithNodesMessage() {
    Path missing = directory.resolve("missing.txt");
    MemoryHttpResponse response = new MemoryHttpResponse();
    assertThatThrownBy(() -> response.download(missing))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessage("ENOENT: no such file or directory, stat '" + missing.toAbsolutePath() + "'");
    assertThat(response.headersSent()).isFalse();
  }

  @Test
  void contentDispositionEncodesNonLatinNames() {
    assertThat(HttpResponse.contentDisposition("dir/año.pdf")).isEqualTo("attachment; filename=\"año.pdf\"");
    assertThat(HttpResponse.contentDisposition("日本.txt"))
        .isEqualTo("attachment; filename=\"??.txt\"; filename*=UTF-8''%E6%97%A5%E6%9C%AC.txt");
  }
}
