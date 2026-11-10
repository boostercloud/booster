package com.boostercloud.framework.provider.azure.infrastructure.azure;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

/**
 * {@link HttpTransport} over {@link HttpClient} (HTTP/1.1, no connect timeout, redirects not followed): file bodies are
 * streamed from disk ({@code fs.createReadStream} in TypeScript).
 */
public final class JdkHttpTransport implements HttpTransport {

  private final HttpClient client;

  /** Creates a transport with its own client. */
  public JdkHttpTransport() {
    this(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).build());
  }

  /**
   * @param client the client to use
   */
  public JdkHttpTransport(HttpClient client) {
    this.client = client;
  }

  @Override
  public Response send(Request request) {
    HttpRequest.BodyPublisher publisher;
    try {
      if (request.body() instanceof byte[] bytes) {
        publisher = HttpRequest.BodyPublishers.ofByteArray(bytes);
      } else if (request.body() instanceof Path path) {
        publisher = HttpRequest.BodyPublishers.ofFile(path);
      } else {
        publisher = HttpRequest.BodyPublishers.noBody();
      }
    } catch (FileNotFoundException e) {
      throw new UncheckedIOException("Could not read " + request.body() + ": " + e.getMessage(), e);
    }
    HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri()).method(request.method(), publisher);
    request.headers().forEach(builder::header);
    if (request.timeout() != null) {
      builder.timeout(request.timeout());
    }
    try {
      HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
      return new Response(response.statusCode(), response.headers().map(), response.body());
    } catch (IOException e) {
      throw new UncheckedIOException(
          request.method() + " " + request.uri() + " failed: " + (e.getMessage() == null ? e : e.getMessage()), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while calling " + request.uri(), e);
    }
  }
}
