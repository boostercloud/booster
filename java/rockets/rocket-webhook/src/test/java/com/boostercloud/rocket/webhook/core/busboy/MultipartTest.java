package com.boostercloud.rocket.webhook.core.busboy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/**
 * Port of Busboy's own multipart tests ({@code busboy@1.6.0/test/test-types-multipart.js} and
 * {@code test-types-multipart-charsets.js}, MIT licence, Brian White), the reference of the parser this rocket ports:
 * every case runs with the source chunks as written and byte by byte, and must produce exactly Busboy's events.
 */
class MultipartTest {

  private static final String B1 = "---------------------------paZqsnEHRufoShdX6fh0lUhXBP4k";
  private static final String B2 = "----WebKitFormBoundaryTB2MiQ36fnSJlrhY";

  /** An event as the Busboy test records it. */
  sealed interface Result permits FieldResult, FileResult, ErrorResult, LimitResult {}

  record FieldResult(String name, String val, Multipart.FieldInfo info) implements Result {}

  /** A file; {@code data} is compared as text (ISO-8859-1, byte per char). */
  record FileResult(String name, String data, Multipart.FileInfo info, boolean limited, String err) implements Result {
    static FileResult of(String name, String data, String filename, String encoding, String mimeType, boolean limited) {
      return new FileResult(name, data, new Multipart.FileInfo(filename, encoding, mimeType), limited, null);
    }

    FileResult withErr(String error) {
      return new FileResult(name, data, info, limited, error);
    }
  }

  record ErrorResult(String error) implements Result {}

  record LimitResult(String event) implements Result {}

  /** A test case of the Busboy suite. */
  record Case(String what, List<String> source, String boundary, Map<String, Long> limits, boolean preservePath,
      List<String> events, List<Result> expected) {
    @Override
    public String toString() {
      return what;
    }
  }

  private static FieldResult field(String name, String val, boolean valueTruncated, String mimeType) {
    return new FieldResult(name, val, new Multipart.FieldInfo(false, valueTruncated, "7bit", mimeType));
  }

  private static FieldResult field(String name, String val) {
    return field(name, val, false, "text/plain");
  }

  private static String lines(String... lines) {
    return String.join("\r\n", lines);
  }

  private static String repeat(String text, int times) {
    return text.repeat(times);
  }

  static Stream<Case> cases() {
    String a1023 = repeat("A", 1023);
    String b1023 = repeat("B", 1023);
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    List<Case> cases = new ArrayList<>();
    cases.add(new Case("Fields and files", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=\"file_name_0\"", "", "super alpha file",
        "--" + B1, "Content-Disposition: form-data; name=\"file_name_1\"", "", "super beta file",
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"1k_a.dat\"",
        "Content-Type: application/octet-stream", "", a1023,
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_1\"; filename=\"1k_b.dat\"",
        "Content-Type: application/octet-stream", "", b1023,
        "--" + B1 + "--")), B1, Map.of(), false, null, List.of(
            field("file_name_0", "super alpha file"),
            field("file_name_1", "super beta file"),
            FileResult.of("upload_file_0", a1023, "1k_a.dat", "7bit", "application/octet-stream", false),
            FileResult.of("upload_file_1", b1023, "1k_b.dat", "7bit", "application/octet-stream", false))));
    cases.add(new Case("Fields only", List.of(lines(
        "--" + B2, "Content-Disposition: form-data; name=\"cont\"", "", "some random content",
        "--" + B2, "Content-Disposition: form-data; name=\"pass\"", "", "some random pass",
        "--" + B2, "Content-Disposition: form-data; name=bit", "", "2",
        "--" + B2 + "--")), B2, Map.of(), false, null, List.of(
            field("cont", "some random content"), field("pass", "some random pass"), field("bit", "2"))));
    cases.add(new Case("No fields and no files", List.of(""), B2, Map.of(), false, null,
        List.of(new ErrorResult("Unexpected end of form"))));
    String limitsSource = lines(
        "--" + B1, "Content-Disposition: form-data; name=\"file_name_0\"", "", "super alpha file",
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"1k_a.dat\"",
        "Content-Type: application/octet-stream", "", alphabet,
        "--" + B1 + "--");
    cases.add(new Case("Fields and files (limits)", List.of(limitsSource), B1,
        Map.of("fileSize", 13L, "fieldSize", 5L), false, null, List.of(
            field("file_name_0", "super", true, "text/plain"),
            FileResult.of("upload_file_0", "ABCDEFGHIJKLM", "1k_a.dat", "7bit", "application/octet-stream", true))));
    cases.add(new Case("Fields and files (limits: 0 files)", List.of(limitsSource), B1, Map.of("files", 0L), false,
        null, List.of(field("file_name_0", "super alpha file"), new LimitResult("filesLimit"))));
    cases.add(new Case("Fields and (ignored) files", cases.getFirst().source(), B1, Map.of(), false, List.of("field"),
        List.of(field("file_name_0", "super alpha file"), field("file_name_1", "super beta file"))));
    String pathsSource = lines(
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"/tmp/1k_a.dat\"",
        "Content-Type: application/octet-stream", "", alphabet,
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_1\"; filename=\"C:\\files\\1k_b.dat\"",
        "Content-Type: application/octet-stream", "", alphabet,
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_2\"; filename=\"relative/1k_c.dat\"",
        "Content-Type: application/octet-stream", "", alphabet,
        "--" + B1 + "--");
    cases.add(new Case("Files with filenames containing paths", List.of(pathsSource), B1, Map.of(), false, null,
        List.of(
            FileResult.of("upload_file_0", alphabet, "1k_a.dat", "7bit", "application/octet-stream", false),
            FileResult.of("upload_file_1", alphabet, "1k_b.dat", "7bit", "application/octet-stream", false),
            FileResult.of("upload_file_2", alphabet, "1k_c.dat", "7bit", "application/octet-stream", false))));
    String preservedSource = lines(
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"/absolute/1k_a.dat\"",
        "Content-Type: application/octet-stream", "", alphabet,
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_1\"; filename=\"C:\\absolute\\1k_b.dat\"",
        "Content-Type: application/octet-stream", "", alphabet,
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_2\"; filename=\"relative/1k_c.dat\"",
        "Content-Type: application/octet-stream", "", alphabet,
        "--" + B1 + "--");
    cases.add(new Case("Paths to be preserved through the preservePath option", List.of(preservedSource), B1,
        Map.of(), true, null, List.of(
            FileResult.of("upload_file_0", alphabet, "/absolute/1k_a.dat", "7bit", "application/octet-stream", false),
            FileResult.of("upload_file_1", alphabet, "C:\\absolute\\1k_b.dat", "7bit", "application/octet-stream",
                false),
            FileResult.of("upload_file_2", alphabet, "relative/1k_c.dat", "7bit", "application/octet-stream",
                false))));
    cases.add(new Case("Empty content-type and empty content-disposition", List.of(lines(
        "--" + B2, "Content-Disposition: form-data; name=\"cont\"", "Content-Type: ", "", "some random content",
        "--" + B2, "Content-Disposition: ", "", "some random pass",
        "--" + B2 + "--")), B2, Map.of(), false, null, List.of(field("cont", "some random content"))));
    cases.add(new Case("Unicode filenames", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=\"file\"; filename*=utf-8''n%C3%A4me.txt",
        "Content-Type: application/octet-stream", "", alphabet,
        "--" + B1 + "--")), B1, Map.of(), false, null, List.of(
            FileResult.of("file", alphabet, "n\u00e4me.txt", "7bit", "application/octet-stream", false))));
    cases.add(new Case("Stopped mid-header", List.of(String.join(":)",
        "--asdasdasdasd\r\n", "Content-Type: text/plain\r\n", "Content-Disposition: form-data; name=\"foo\"\r\n",
        "\r\n", "asd\r\n", "--asdasdasdasd--")), "asdasdasdasd", Map.of(), false, null, List.of(
            new ErrorResult("Malformed part header"), new ErrorResult("Unexpected end of form"))));
    cases.add(new Case("content-type for fields", List.of(lines(
        "--" + B2, "Content-Disposition: form-data; name=\"cont\"", "Content-Type: application/json", "", "{}",
        "--" + B2 + "--")), B2, Map.of(), false, null, List.of(field("cont", "{}", false, "application/json"))));
    cases.add(new Case("empty form", List.of("--" + B2 + "--"), B2, Map.of(), false, null, List.of()));
    cases.add(new Case("Stopped mid-file #1", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=upload_file_0; filename=\"1k_a.dat\"",
        "Content-Type: application/octet-stream", "Content-Transfer-Encoding: binary", "", "")), B1, Map.of(), false,
        null, List.of(
            FileResult.of("upload_file_0", "", "1k_a.dat", "binary", "application/octet-stream", false)
                .withErr("Unexpected end of form"),
            new ErrorResult("Unexpected end of form"))));
    cases.add(new Case("Stopped mid-file #2", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=upload_file_0; filename=\"1k_a.dat\"",
        "Content-Type: application/octet-stream", "", "a")), B1, Map.of(), false, null, List.of(
            FileResult.of("upload_file_0", "a", "1k_a.dat", "7bit", "application/octet-stream", false)
                .withErr("Unexpected end of form"),
            new ErrorResult("Unexpected end of form"))));
    cases.add(new Case("Text file with charset", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"notes.txt\"",
        "Content-Type: text/plain; charset=utf8", "", "a",
        "--" + B1 + "--")), B1, Map.of(), false, null, List.of(
            FileResult.of("upload_file_0", "a", "notes.txt", "7bit", "text/plain", false))));
    cases.add(new Case("Folded header value", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"notes.txt\"",
        "Content-Type: ", " text/plain; charset=utf8", "", "a",
        "--" + B1 + "--")), B1, Map.of(), false, null, List.of(
            FileResult.of("upload_file_0", "a", "notes.txt", "7bit", "text/plain", false))));
    cases.add(new Case("No Content-Disposition", List.of(lines(
        "--" + B1, "Content-Type: text/plain; charset=utf8", "", "a",
        "--" + B1 + "--")), B1, Map.of(), false, null, List.of()));
    cases.add(new Case("Skip field parts if no listener", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=\"file_name_0\"", "", repeat("a", 64 * 1024),
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"notes.txt\"",
        "Content-Type: ", " text/plain; charset=utf8", "", "bc",
        "--" + B1 + "--")), B1, Map.of("fieldSize", Long.MAX_VALUE), false, List.of("file"), List.of(
            FileResult.of("upload_file_0", "bc", "notes.txt", "7bit", "text/plain", false))));
    cases.add(new Case("Parts limit", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=\"file_name_0\"", "", "a",
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"notes.txt\"",
        "Content-Type: ", " text/plain; charset=utf8", "", "bc",
        "--" + B1 + "--")), B1, Map.of("parts", 1L), false, null, List.of(
            field("file_name_0", "a"), new LimitResult("partsLimit"))));
    cases.add(new Case("Fields limit", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=\"file_name_0\"", "", "a",
        "--" + B1, "Content-Disposition: form-data; name=\"file_name_1\"", "", "b",
        "--" + B1 + "--")), B1, Map.of("fields", 1L), false, null, List.of(
            field("file_name_0", "a"), new LimitResult("fieldsLimit"))));
    cases.add(new Case("Files limit", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"notes.txt\"",
        "Content-Type: text/plain; charset=utf8", "", "ab",
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_1\"; filename=\"notes2.txt\"",
        "Content-Type: text/plain; charset=utf8", "", "cd",
        "--" + B1 + "--")), B1, Map.of("files", 1L), false, null, List.of(
            FileResult.of("upload_file_0", "ab", "notes.txt", "7bit", "text/plain", false),
            new LimitResult("filesLimit"))));
    cases.add(new Case("Oversized part header", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"" + repeat("a", 64 * 1024)
            + ".txt\"",
        "Content-Type: text/plain; charset=utf8", "", "ab",
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_1\"; filename=\"notes2.txt\"",
        "Content-Type: text/plain; charset=utf8", "", "cd",
        "--" + B1 + "--")), B1, Map.of(), false, null, List.of(
            new ErrorResult("Malformed part header"),
            FileResult.of("upload_file_1", "cd", "notes2.txt", "7bit", "text/plain", false))));
    cases.add(new Case("Lookbehind data should not stall file streams", List.of(
        lines("--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"notes.txt\"",
            "Content-Type: text/plain; charset=utf8", "", repeat("a", 31) + "\r"),
        repeat("b", 40),
        "\r\n--" + B1 + "--"), B1, Map.of(), false, null, List.of(
            FileResult.of("upload_file_0", repeat("a", 31) + "\r" + repeat("b", 40), "notes.txt", "7bit",
                "text/plain", false))));
    cases.add(new Case("Header size limit should be per part", List.of(lines(
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"" + repeat("a", 8 * 1024)
            + ".txt\"", "Content-Type: text/plain; charset=utf8", "", "ab",
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_1\"; filename=\"" + repeat("b", 8 * 1024)
            + ".txt\"", "Content-Type: text/plain; charset=utf8", "", "cd",
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_2\"; filename=\"" + repeat("c", 8 * 1024)
            + ".txt\"", "Content-Type: text/plain; charset=utf8", "", "ef",
        "--" + B1 + "--")), B1, Map.of(), false, null, List.of(
            FileResult.of("upload_file_0", "ab", repeat("a", 8 * 1024) + ".txt", "7bit", "text/plain", false),
            FileResult.of("upload_file_1", "cd", repeat("b", 8 * 1024) + ".txt", "7bit", "text/plain", false),
            FileResult.of("upload_file_2", "ef", repeat("c", 8 * 1024) + ".txt", "7bit", "text/plain", false))));
    cases.add(new Case("Empty part", List.of(
        "\r\n--d1bf46b3-aa33-4061-b28d-6c5ced8b08ee\r\n",
        "Content-Type: application/gzip\r\nContent-Encoding: gzip\r\n"
            + "Content-Disposition: form-data; name=batch-1; filename=batch-1\r\n\r\n",
        "\r\n--d1bf46b3-aa33-4061-b28d-6c5ced8b08ee--"), "d1bf46b3-aa33-4061-b28d-6c5ced8b08ee", Map.of(), false, null,
        List.of(FileResult.of("batch-1", "", "batch-1", "7bit", "application/gzip", false))));
    return cases.stream();
  }

  static Stream<Arguments> casesInBothModes() {
    return cases().flatMap(c -> Stream.of(Arguments.of(c, false), Arguments.of(c, true)));
  }

  @ParameterizedTest(name = "{0} (byte by byte: {1})")
  @MethodSource("casesInBothModes")
  void produces_busboys_events(Case testCase, boolean byteByByte) {
    Busboy.Options options = new Busboy.Options(null, null, testCase.preservePath(),
        testCase.limits().get("fieldSize"), testCase.limits().get("fileSize"), testCase.limits().get("files"),
        testCase.limits().get("fields"), testCase.limits().get("parts"));
    Recorder recorder = new Recorder(testCase.events());
    Multipart bb = Busboy.create(Map.of("content-type", "multipart/form-data; boundary=" + testCase.boundary()),
        options, recorder);
    for (String source : testCase.source()) {
      byte[] buf = source.getBytes(StandardCharsets.UTF_8);
      if (byteByByte) {
        for (byte b : buf) {
          bb.write(new byte[] {b});
        }
      } else {
        bb.write(buf);
      }
    }
    bb.end();
    assertThat(recorder.results()).containsExactlyElementsOf(testCase.expected());
  }

  @Test
  void decodes_non_extended_parameters_with_def_param_charset() {
    String input = lines(
        "--" + B1, "Content-Disposition: form-data; name=\"upload_file_0\"; filename=\"\u30c6\u30b9\u30c8.dat\"",
        "Content-Type: application/octet-stream", "", repeat("A", 1023),
        "--" + B1 + "--");
    Recorder recorder = new Recorder(null);
    Multipart bb = Busboy.create(Map.of("content-type", "multipart/form-data; boundary=" + B1),
        new Busboy.Options(null, "utf8", null, null, null, null, null, null), recorder);
    bb.write(input.getBytes(StandardCharsets.UTF_8));
    bb.end();
    assertThat(recorder.results()).containsExactly(FileResult.of("upload_file_0", repeat("A", 1023),
        "\u30c6\u30b9\u30c8.dat", "7bit", "application/octet-stream", false));
  }

  @Test
  void keeps_non_extended_parameters_as_latin1_characters_by_default() {
    String input = lines(
        "--" + B1, "Content-Disposition: form-data; name=\"f\"; filename=\"\u00e4.txt\"", "", "x",
        "--" + B1 + "--");
    Recorder recorder = new Recorder(null);
    Multipart bb = Busboy.create(Map.of("content-type", "multipart/form-data; boundary=" + B1), null, recorder);
    bb.write(input.getBytes(StandardCharsets.UTF_8));
    bb.end();
    // UTF-8 bytes C3 A4 read as two Latin-1 characters
    assertThat(((FileResult) recorder.results().getFirst()).info().filename()).isEqualTo("\u00c3\u00a4.txt");
  }

  @Test
  void decodes_field_values_with_the_part_charset() {
    byte[] latin1Value = "caf\u00e9".getBytes(StandardCharsets.ISO_8859_1);
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.writeBytes(lines("--" + B1, "Content-Disposition: form-data; name=\"a\"",
        "Content-Type: text/plain; charset=ISO-8859-1", "", "").getBytes(StandardCharsets.US_ASCII));
    body.writeBytes(latin1Value);
    body.writeBytes(("\r\n--" + B1 + "\r\nContent-Disposition: form-data; name=\"b\"\r\n\r\ncaf\u00e9\r\n--" + B1 + "--")
        .getBytes(StandardCharsets.UTF_8));
    Recorder recorder = new Recorder(null);
    Multipart bb = Busboy.create(Map.of("content-type", "multipart/form-data; boundary=" + B1), null, recorder);
    bb.write(body.toByteArray());
    bb.end();
    assertThat(recorder.results()).containsExactly(
        field("a", "caf\u00e9"), field("b", "caf\u00e9"));
  }

  @Test
  void ignores_the_preamble_and_the_writes_after_the_closing_boundary() {
    String form = "preamble text\r\n--" + B1 + "\r\nContent-Disposition: form-data; name=\"a\"\r\n\r\n1\r\n--" + B1
        + "--";
    // Busboy quirk: after the closing boundary the search goes on over the rest of the same chunk, so two boundaries
    // in a row there start a new part
    String epilogue = "\r\n--" + B1 + "\r\n--" + B1 + "\r\nContent-Disposition: form-data; name=\"b\"\r\n\r\n2\r\n--"
        + B1 + "--";

    Recorder separate = new Recorder(null);
    Multipart bb = Busboy.create(Map.of("content-type", "multipart/form-data; boundary=" + B1), null, separate);
    bb.write(form.getBytes(StandardCharsets.UTF_8));
    bb.write(epilogue.getBytes(StandardCharsets.UTF_8));
    bb.end();
    assertThat(separate.results()).containsExactly(field("a", "1"));
    assertThat(bb.isComplete()).isTrue();

    Recorder together = new Recorder(null);
    Multipart same = Busboy.create(Map.of("content-type", "multipart/form-data; boundary=" + B1), null, together);
    same.write((form + epilogue).getBytes(StandardCharsets.UTF_8));
    same.end();
    assertThat(together.results()).containsExactly(field("a", "1"), field("b", "2"));
  }

  @Test
  void keeps_binary_file_content_byte_for_byte() {
    byte[] content = new byte[256 * 3];
    for (int i = 0; i < content.length; i++) {
      content[i] = (byte) i;
    }
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.writeBytes(("--" + B1 + "\r\nContent-Disposition: form-data; name=\"bin\"; filename=\"b.bin\"\r\n\r\n")
        .getBytes(StandardCharsets.US_ASCII));
    body.writeBytes(content);
    body.writeBytes(("\r\n--" + B1 + "--").getBytes(StandardCharsets.US_ASCII));
    byte[] bytes = body.toByteArray();
    for (int chunkSize : new int[] {1, 2, 3, 7, 61, 1000, bytes.length}) {
      ByteCollector collector = new ByteCollector();
      Multipart bb = Busboy.create(Map.of("content-type", "multipart/form-data; boundary=" + B1), null, collector);
      for (int i = 0; i < bytes.length; i += chunkSize) {
        bb.write(Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + chunkSize)));
      }
      bb.end();
      assertThat(collector.content.toByteArray()).as("chunk size %d", chunkSize).isEqualTo(content);
    }
  }

  @Test
  void the_default_listener_throws_the_errors() {
    Multipart bb = Busboy.create(Map.of("content-type", "multipart/form-data; boundary=x"), null,
        new ByteCollector());
    assertThatThrownBy(bb::end).isInstanceOf(IllegalStateException.class).hasMessage("Unexpected end of form");
  }

  @Test
  void rejects_bad_content_types() {
    Recorder recorder = new Recorder(null);
    assertThatThrownBy(() -> Busboy.create(Map.of(), null, recorder)).hasMessage("Missing Content-Type");
    assertThatThrownBy(() -> Busboy.create(null, null, recorder)).hasMessage("Missing Content-Type");
    assertThatThrownBy(() -> Busboy.create(Map.of("content-type", "multipart/form-data;"), null, recorder))
        .hasMessage("Malformed content type");
    assertThatThrownBy(() -> Busboy.create(Map.of("content-type", "text/plain"), null, recorder))
        .hasMessage("Unsupported content type: text/plain");
    assertThatThrownBy(() -> Busboy.create(Map.of("content-type", "multipart/form-data"), null, recorder))
        .hasMessage("Multipart: Boundary not found");
  }

  /** Records the events like the Busboy test harness (an {@code 'error'} listener included). */
  private static final class Recorder implements Multipart.Listener {
    private final List<String> events;
    private final List<Object> results = new ArrayList<>();

    Recorder(List<String> events) {
      this.events = events;
    }

    List<Result> results() {
      List<Result> finished = new ArrayList<>();
      for (Object result : results) {
        finished.add(result instanceof FileRecorder recorder ? recorder.result() : (Result) result);
      }
      return finished;
    }

    @Override
    public boolean listensToFiles() {
      return events == null || events.contains("file");
    }

    @Override
    public boolean listensToFields() {
      return events == null || events.contains("field");
    }

    @Override
    public Multipart.FileListener file(String name, Multipart.FileInfo info) {
      FileRecorder recorder = new FileRecorder(name, info);
      results.add(recorder);
      return recorder;
    }

    @Override
    public void field(String name, String value, Multipart.FieldInfo info) {
      results.add(new FieldResult(name, value, info));
    }

    @Override
    public void error(IllegalStateException error) {
      results.add(new ErrorResult(error.getMessage()));
    }

    @Override
    public void partsLimit() {
      results.add(new LimitResult("partsLimit"));
    }

    @Override
    public void filesLimit() {
      results.add(new LimitResult("filesLimit"));
    }

    @Override
    public void fieldsLimit() {
      results.add(new LimitResult("fieldsLimit"));
    }
  }

  private static final class FileRecorder implements Multipart.FileListener {
    private final String name;
    private final Multipart.FileInfo info;
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private boolean limited;
    private String err;

    FileRecorder(String name, Multipart.FileInfo info) {
      this.name = name;
      this.info = info;
    }

    @Override
    public void data(byte[] bytes, int offset, int length) {
      data.write(bytes, offset, length);
    }

    @Override
    public void limit() {
      limited = true;
    }

    @Override
    public void end() {}

    @Override
    public void error(IllegalStateException error) {
      err = error.getMessage();
    }

    FileResult result() {
      return new FileResult(name, data.toString(StandardCharsets.UTF_8), info, limited, err);
    }
  }

  private static final class ByteCollector implements Multipart.Listener {
    final ByteArrayOutputStream content = new ByteArrayOutputStream();

    @Override
    public Multipart.FileListener file(String name, Multipart.FileInfo info) {
      return new Multipart.FileListener() {
        @Override
        public void data(byte[] bytes, int offset, int length) {
          content.write(bytes, offset, length);
        }

        @Override
        public void end() {}
      };
    }

    @Override
    public void field(String name, String value, Multipart.FieldInfo info) {}
  }
}
