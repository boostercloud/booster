package com.boostercloud.rocket.webhook.core.busboy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The header parsers and decoders of {@code busboy/lib/utils.js}. */
class BusboyUtilsTest {

  @Test
  void parses_content_types() {
    BusboyUtils.ContentType type = BusboyUtils.parseContentType("Multipart/Form-Data; Boundary=abc; charset=\"utf-8\"");
    assertThat(type.type()).isEqualTo("multipart");
    assertThat(type.subtype()).isEqualTo("form-data");
    assertThat(type.params()).containsExactly(Map.entry("boundary", "abc"), Map.entry("charset", "utf-8"));
  }

  @Test
  void keeps_the_first_occurrence_of_a_parameter_and_unescapes_quoted_pairs() {
    BusboyUtils.ContentType type = BusboyUtils.parseContentType("text/plain; a=\"x\\\"y\\\\z\"; a=2");
    assertThat(type.params()).containsExactly(Map.entry("a", "x\"y\\z"));
  }

  @Test
  void rejects_malformed_content_types() {
    assertThat(BusboyUtils.parseContentType("")).isNull();
    assertThat(BusboyUtils.parseContentType("text")).isNull();
    assertThat(BusboyUtils.parseContentType("/plain")).isNull();
    assertThat(BusboyUtils.parseContentType("text/")).isNull();
    assertThat(BusboyUtils.parseContentType("text/plain;")).isNull();
    assertThat(BusboyUtils.parseContentType("text/plain; a")).isNull();
    assertThat(BusboyUtils.parseContentType("text/plain; a=\"unterminated")).isNull();
  }

  @Test
  void parses_dispositions_with_extended_parameters() {
    BusboyUtils.Disposition disposition = BusboyUtils.parseDisposition(
        "form-data; name=\"file\"; filename*=utf-8''n%C3%A4me.txt", BusboyUtils.NULL_DECODER);
    assertThat(disposition.type()).isEqualTo("form-data");
    assertThat(disposition.params()).containsEntry("name", "file").containsEntry("filename*", "n\u00e4me.txt");
  }

  @Test
  void decodes_non_extended_values_with_the_default_decoder() {
    String latin1OfUtf8 = new String("\u00e4".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    assertThat(BusboyUtils.parseDisposition("form-data; filename=\"" + latin1OfUtf8 + "\"",
        BusboyUtils.getDecoder("utf8")).params()).containsEntry("filename", "\u00e4");
    assertThat(BusboyUtils.parseDisposition("form-data; filename=\"" + latin1OfUtf8 + "\"",
        BusboyUtils.NULL_DECODER).params()).containsEntry("filename", latin1OfUtf8);
  }

  @Test
  void rejects_malformed_dispositions() {
    assertThat(BusboyUtils.parseDisposition("", BusboyUtils.NULL_DECODER)).isNull();
    assertThat(BusboyUtils.parseDisposition("form-data; name", BusboyUtils.NULL_DECODER)).isNull();
    assertThat(BusboyUtils.parseDisposition("form-data; filename*=utf-8''%G1", BusboyUtils.NULL_DECODER)).isNull();
  }

  @Test
  void decodes_charsets() {
    byte[] utf8 = "caf\u00e9".getBytes(StandardCharsets.UTF_8);
    assertThat(BusboyUtils.convertToUTF8(utf8, "utf8")).isEqualTo("caf\u00e9");
    assertThat(BusboyUtils.convertToUTF8(utf8, "UTF-8")).isEqualTo("caf\u00e9");
    assertThat(BusboyUtils.convertToUTF8("caf\u00e9".getBytes(StandardCharsets.ISO_8859_1), "latin1"))
        .isEqualTo("caf\u00e9");
    assertThat(BusboyUtils.convertToUTF8("ab".getBytes(StandardCharsets.UTF_16LE), "utf16le")).isEqualTo("ab");
    assertThat(BusboyUtils.convertToUTF8("ab".getBytes(StandardCharsets.US_ASCII), "base64")).isEqualTo("YWI=");
    assertThat(BusboyUtils.convertToUTF8("x".getBytes(StandardCharsets.US_ASCII), "no-such-charset")).isNull();
    assertThat(BusboyUtils.convertToUTF8(new byte[0], "no-such-charset")).isEmpty();
  }

  @Test
  void takes_base_names() {
    assertThat(BusboyUtils.basename("/tmp/a.txt")).isEqualTo("a.txt");
    assertThat(BusboyUtils.basename("C:\\files\\b.txt")).isEqualTo("b.txt");
    assertThat(BusboyUtils.basename("relative/c.txt")).isEqualTo("c.txt");
    assertThat(BusboyUtils.basename("..")).isEmpty();
    assertThat(BusboyUtils.basename("dir/.")).isEmpty();
    assertThat(BusboyUtils.basename("plain")).isEqualTo("plain");
  }
}
