package com.boostercloud.rocket.webhook.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.rocket.webhook.support.MultipartBody;
import com.boostercloud.rocket.webhook.types.MultiPartConfig;
import com.boostercloud.rocket.webhook.types.WebhookHttpRequest;
import com.boostercloud.rocket.webhook.types.WebhookMultipartForm;
import com.boostercloud.rocket.webhook.types.WebhookParsedField;
import com.boostercloud.rocket.webhook.types.WebhookParsedFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * {@code parse-multi-part.ts} ({@code parseMultipartFormData}): Busboy with the request headers and the webhook's
 * {@code MultiPartConfig}, every file buffered, fields in body order and files in end order.
 */
class ParseMultiPartTest {

  private static WebhookHttpRequest.Builder multipart() {
    return WebhookHttpRequest.builder("POST", "/upload").header("content-type", MultipartBody.CONTENT_TYPE);
  }

  private static byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    new Random(42).nextBytes(bytes);
    return bytes;
  }

  @Test
  void buffers_fields_and_files_of_a_streamed_body_in_chunks() {
    byte[] big = randomBytes(3 * ParseMultiPart.CHUNK_SIZE + 17);
    byte[] body = MultipartBody.create()
        .field("first", "1")
        .file("big", "big.bin", "application/octet-stream", big)
        .field("second", "café")
        .file("small", "small.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8))
        .build();
    AtomicBoolean closed = new AtomicBoolean();
    InputStream stream = new ByteArrayInputStream(body) {
      @Override
      public void close() throws IOException {
        closed.set(true);
        super.close();
      }
    };

    WebhookMultipartForm form = ParseMultiPart.parseMultipartFormData(multipart().bodyStream(() -> stream).build(),
        null);

    assertThat(form.fields()).containsExactly(
        new WebhookParsedField("first", "1", false, false, "7bit", "text/plain"),
        new WebhookParsedField("second", "café", false, false, "7bit", "text/plain"));
    assertThat(form.files()).containsExactly(
        new WebhookParsedFile("big", big, "big.bin", "7bit", "application/octet-stream"),
        new WebhookParsedFile("small", "hello".getBytes(StandardCharsets.UTF_8), "small.txt", "7bit", "text/plain"));
    assertThat(closed).isTrue();
  }

  @Test
  void a_buffered_body_is_parsed_at_once_and_a_string_body_is_utf8() {
    byte[] body = MultipartBody.create().field("name", "välue").build();

    WebhookMultipartForm fromBytes = ParseMultiPart.parseMultipartFormData(multipart().body(body).build(), null);
    WebhookMultipartForm fromText = ParseMultiPart.parseMultipartFormData(
        multipart().body(new String(body, StandardCharsets.UTF_8)).build(), null);

    assertThat(fromBytes.fields()).singleElement().extracting(WebhookParsedField::value).isEqualTo("välue");
    assertThat(fromText).isEqualTo(fromBytes);
  }

  @Test
  void an_empty_form_has_no_parts_and_a_missing_body_is_an_unexpected_end() {
    byte[] empty = ("--" + MultipartBody.BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);

    assertThat(ParseMultiPart.parseMultipartFormData(multipart().body(empty).build(), null))
        .isEqualTo(new WebhookMultipartForm(java.util.List.of(), java.util.List.of()));
    assertThatThrownBy(() -> ParseMultiPart.parseMultipartFormData(multipart().build(), null))
        .isInstanceOf(IllegalStateException.class).hasMessage("Unexpected end of form");
  }

  @Test
  void limits_truncate_files_and_drop_the_parts_over_them() {
    byte[] body = MultipartBody.create()
        .file("a", "a.txt", "text/plain", "0123456789".getBytes(StandardCharsets.UTF_8))
        .file("b", "b.txt", "text/plain", "abc".getBytes(StandardCharsets.UTF_8))
        .field("c", "3")
        .build();

    WebhookMultipartForm truncated = ParseMultiPart.parseMultipartFormData(multipart().body(body).build(),
        MultiPartConfig.of(MultiPartConfig.Limits.fileSize(5)));
    WebhookMultipartForm oneFile = ParseMultiPart.parseMultipartFormData(multipart().body(body).build(),
        MultiPartConfig.of(new MultiPartConfig.Limits(null, 1L, null)));
    WebhookMultipartForm twoParts = ParseMultiPart.parseMultipartFormData(multipart().body(body).build(),
        MultiPartConfig.of(new MultiPartConfig.Limits(null, null, 2L)));

    assertThat(truncated.files()).extracting(file -> new String(file.bufferFile(), StandardCharsets.UTF_8))
        .containsExactly("01234", "abc");
    assertThat(oneFile.files()).extracting(WebhookParsedFile::name).containsExactly("a");
    assertThat(oneFile.fields()).extracting(WebhookParsedField::name).containsExactly("c");
    assertThat(twoParts.files()).extracting(WebhookParsedFile::name).containsExactly("a", "b");
    assertThat(twoParts.fields()).isEmpty();
  }

  @Test
  void the_param_charset_and_preserve_path_options_reach_busboy() {
    String boundary = MultipartBody.BOUNDARY;
    byte[] nameBytes = "ñ.txt".getBytes(StandardCharsets.UTF_8);
    byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"f\"; filename=\"dir/")
        .getBytes(StandardCharsets.ISO_8859_1);
    byte[] tail = ("\"\r\nContent-Type: text/plain\r\n\r\nx\r\n--" + boundary + "--\r\n")
        .getBytes(StandardCharsets.ISO_8859_1);
    byte[] body = new byte[head.length + nameBytes.length + tail.length];
    System.arraycopy(head, 0, body, 0, head.length);
    System.arraycopy(nameBytes, 0, body, head.length, nameBytes.length);
    System.arraycopy(tail, 0, body, head.length + nameBytes.length, tail.length);

    WebhookParsedFile latin1 = ParseMultiPart.parseMultipartFormData(multipart().body(body).build(), null)
        .files().getFirst();
    WebhookParsedFile utf8 = ParseMultiPart.parseMultipartFormData(multipart().body(body).build(),
        new MultiPartConfig("utf8", true, null)).files().getFirst();

    assertThat(latin1.filename()).isEqualTo(new String(nameBytes, StandardCharsets.ISO_8859_1));
    assertThat(utf8.filename()).isEqualTo("dir/ñ.txt");
  }

  @Test
  void malformed_requests_fail_with_the_busboy_messages() {
    assertThatThrownBy(() -> ParseMultiPart.parseMultipartFormData(
        WebhookHttpRequest.builder("POST", "/u").header("content-type", "multipart/form-data").build(), null))
        .isInstanceOf(IllegalStateException.class).hasMessage("Multipart: Boundary not found");
    byte[] badHeader = MultipartBody.create().file("f", "a".repeat(64 * 1024) + ".txt", "text/plain", new byte[] {1})
        .build();
    assertThatThrownBy(() -> ParseMultiPart.parseMultipartFormData(multipart().body(badHeader).build(), null))
        .isInstanceOf(IllegalStateException.class).hasMessage("Malformed part header");
    assertThatThrownBy(() -> ParseMultiPart.parseMultipartFormData(multipart().body(42).build(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The \"chunk\" argument must be of type string or an instance of Buffer, TypedArray, or DataView. "
            + "Received an instance of Integer");
  }

  @Test
  void read_failures_of_the_stream_are_unchecked_io_errors() {
    InputStream failing = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("connection reset");
      }
    };

    assertThatThrownBy(() -> ParseMultiPart.parseMultipartFormData(multipart().bodyStream(() -> failing).build(),
        null)).isInstanceOf(UncheckedIOException.class).hasMessageContaining("connection reset");
  }
}
