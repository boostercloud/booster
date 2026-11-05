package io.boostercloud.booster.rockets.webhook;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MultipartParserTest {
  @Test void parsesBinaryContainingBoundaryPrefixWithoutTruncation() {
    byte[] content = "before\r\n--BOUNDARY-not-delimiter\r\nafter".getBytes(StandardCharsets.US_ASCII);
    var form = MultipartParser.parse(WebhookRocketTest.multipartBody("BOUNDARY", "C:\\folder\\name.bin", content),
        "multipart/form-data; boundary=BOUNDARY", MultipartConfig.defaults());
    assertEquals("name.bin", form.files().getFirst().filename());
    assertArrayEquals(content, form.files().getFirst().bufferFile());
    byte[] returned = form.files().getFirst().bufferFile(); returned[0] = 0;
    assertArrayEquals(content, form.files().getFirst().bufferFile());
  }
  @Test void supportsExtendedFilenameAndDeclaredFieldCharset() {
    String body = "--B\r\nContent-Disposition: form-data; name=\"title\"\r\nContent-Type: text/plain; charset=ISO-8859-1\r\n\r\ncafé"
        + "\r\n--B\r\nContent-Disposition: form-data; name=\"f\"; filename*=UTF-8''caf%C3%A9.txt\r\n\r\nabc\r\n--B--\r\n";
    var form = MultipartParser.parse(body.getBytes(StandardCharsets.ISO_8859_1), "multipart/form-data; boundary=B", MultipartConfig.defaults());
    assertEquals("café", form.fields().getFirst().value()); assertEquals("café.txt", form.files().getFirst().filename());
  }
  @Test void rejectsTruncatedBodiesAndDuplicateHeadersAndMalformedBoundaries() {
    byte[] body = WebhookRocketTest.multipartBody("B", "f", new byte[] {1});
    assertThrows(WebhookInputException.class, () -> MultipartParser.parse(java.util.Arrays.copyOf(body, body.length - 8), "multipart/form-data; boundary=B", MultipartConfig.defaults()));
    assertThrows(WebhookInputException.class, () -> MultipartParser.parse(body, "multipart/form-data; boundary=\"unterminated", MultipartConfig.defaults()));
    String repeated = "--B\r\nContent-Disposition: form-data; name=\"a\"\r\nContent-Disposition: form-data; name=\"b\"\r\n\r\nx\r\n--B--\r\n";
    assertThrows(WebhookInputException.class, () -> MultipartParser.parse(repeated.getBytes(StandardCharsets.US_ASCII), "multipart/form-data; boundary=B", MultipartConfig.defaults()));
  }
  @Test void enforcesEachLimitWithoutReturningPartialFilesOrFields() {
    byte[] body = WebhookRocketTest.multipartBody("B", "f", new byte[6]);
    for (MultipartConfig config : new MultipartConfig[] {
        new MultipartConfig(StandardCharsets.ISO_8859_1, false, 5, 1, 2, 8, 1024),
        new MultipartConfig(StandardCharsets.ISO_8859_1, false, 6, 0, 2, 8, 1024),
        new MultipartConfig(StandardCharsets.ISO_8859_1, false, 6, 1, 1, 8, 1024),
        new MultipartConfig(StandardCharsets.ISO_8859_1, false, 6, 1, 2, 4, 1024) }) {
      var error = assertThrows(WebhookInputException.class, () -> MultipartParser.parse(body, "multipart/form-data; boundary=B", config));
      assertEquals(413, error.status);
    }
  }
  @Test void preservesConfiguredPathsAndDoesNotWriteFiles() {
    var defaults = MultipartConfig.defaults();
    var config = new MultipartConfig(defaults.parameterCharset(), true, defaults.maxFileBytes(), defaults.maxFiles(), defaults.maxParts(), defaults.maxFieldBytes(), defaults.maxHeaderBytes());
    var form = MultipartParser.parse(WebhookRocketTest.multipartBody("B", "../../outside", new byte[0]), "multipart/form-data; boundary=B", config);
    assertEquals("../../outside", form.files().getFirst().filename());
  }
  @Test void acceptsPreambleIncludingAnInitialCrLfAndEmptyMultipart() {
    var form = MultipartParser.parse("\r\n--B--\r\n".getBytes(StandardCharsets.US_ASCII), "multipart/form-data; boundary=B", MultipartConfig.defaults());
    assertTrue(form.files().isEmpty()); assertTrue(form.fields().isEmpty());
  }
}
