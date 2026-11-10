package com.boostercloud.rocket.fileuploads.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** A small HTTP/1.1 client for the tests (what {@code curl} does in the rocket's README). */
public final class Http {

  private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(10)).build();

  private Http() {}

  /**
   * @param method the verb
   * @param url the URL (spaces are sent encoded)
   * @param headers the headers
   * @param body the body, or {@code null}
   * @return the response with the body as bytes
   */
  public static HttpResponse<byte[]> send(String method, String url, Map<String, String> headers, byte[] body) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url.replace(" ", "%20")))
        .timeout(Duration.ofSeconds(30))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
    headers.forEach(builder::header);
    try {
      return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /**
   * @param url the URL
   * @param body the raw content
   * @return the response of {@code PUT} with {@code Content-Type: application/octet-stream}
   */
  public static HttpResponse<byte[]> put(String url, byte[] body) {
    return send("PUT", url, Map.of("Content-Type", "application/octet-stream", "x-ms-blob-type", "BlockBlob"), body);
  }

  /**
   * @param url the URL
   * @return the response of {@code GET}
   */
  public static HttpResponse<byte[]> get(String url) {
    return send("GET", url, Map.of(), null);
  }

  /**
   * @param response a response
   * @return its body as UTF-8 text
   */
  public static String text(HttpResponse<byte[]> response) {
    return new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
  }
}
