package com.boostercloud.rocket.webhook.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** A small HTTP/1.1 client for the end-to-end tests (what {@code curl} does in the rocket's README). */
public final class Http {

  private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(10)).build();

  private Http() {}

  /**
   * @param method the verb
   * @param url the URL
   * @param headers the headers
   * @param body the body, or {@code null}
   * @return the response with the body as bytes
   */
  public static HttpResponse<byte[]> send(String method, String url, Map<String, String> headers, byte[] body) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
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
   * @param contentType the content type
   * @param body the UTF-8 body
   * @return the response of {@code POST}
   */
  public static HttpResponse<byte[]> post(String url, String contentType, String body) {
    return send("POST", url, Map.of("Content-Type", contentType), body.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @param url the URL
   * @return the response of {@code GET}
   */
  public static HttpResponse<byte[]> get(String url) {
    return send("GET", url, Map.of(), null);
  }

  /**
   * Sends bytes as they are on a new connection (for requests {@link HttpClient} refuses to build, such as odd
   * {@code Host} headers) and reads until the server closes it; send {@code Connection: close}.
   *
   * @param port the local port
   * @param request the request head and body
   * @return the raw response, decoded as ISO-8859-1
   */
  public static String raw(int port, String request) {
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
      socket.setSoTimeout(30_000);
      socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
      socket.getOutputStream().flush();
      return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @param response a response
   * @return its body as UTF-8 text
   */
  public static String text(HttpResponse<byte[]> response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  /**
   * @param response a response
   * @param name a header name
   * @return its first value, or {@code null}
   */
  public static String header(HttpResponse<byte[]> response, String name) {
    return response.headers().firstValue(name).orElse(null);
  }
}
