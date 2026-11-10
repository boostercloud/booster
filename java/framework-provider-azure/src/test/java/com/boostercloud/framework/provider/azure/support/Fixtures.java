package com.boostercloud.framework.provider.azure.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reads the recorded Azure responses under {@code src/test/resources/fixtures}. */
public final class Fixtures {

  private Fixtures() {}

  /**
   * @param path the path below {@code fixtures/} (e.g. {@code cosmos/container.json})
   * @return the file content
   */
  public static String read(String path) {
    try (InputStream input = Fixtures.class.getResourceAsStream("/fixtures/" + path)) {
      if (input == null) {
        throw new IllegalArgumentException("Missing fixture " + path);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @param path the fixture path
   * @param status the HTTP status
   * @return a JSON response with the fixture as body
   */
  public static FakeAzureServer.Response response(String path, int status) {
    return FakeAzureServer.Response.json(status, read(path));
  }
}
