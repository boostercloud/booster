package com.boostercloud.framework.provider.local.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** A small blocking HTTP/1.1 client for the server tests. */
public final class Http1Client {

  private static final HttpClient CLIENT = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private Http1Client() {}

  public static HttpResponse<String> send(String method, String url, Map<String, String> headers, String body) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    headers.forEach(builder::header);
    try {
      return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  public static HttpResponse<byte[]> sendBytes(String method, String url, Map<String, String> headers, byte[] body) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
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

  public static HttpResponse<String> postJson(String url, String json) {
    return send("POST", url, Map.of("Content-Type", "application/json"), json);
  }

  public static HttpResponse<String> get(String url) {
    return send("GET", url, Map.of(), null);
  }
}
