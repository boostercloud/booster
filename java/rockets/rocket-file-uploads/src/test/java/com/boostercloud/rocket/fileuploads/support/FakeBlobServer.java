package com.boostercloud.rocket.fileuploads.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * An in-JVM HTTP server standing in for Azure Blob Storage (and the token endpoints) in the contract tests: it
 * records every request exactly as received and answers with the test's handler. Unhandled requests get 599.
 */
public final class FakeBlobServer implements AutoCloseable {

  /** A recorded request. */
  public record RecordedRequest(String method, String rawPath, String rawQuery, Map<String, String> headers,
      byte[] body) {

    /**
     * @param name a header name (any case)
     * @return its value, or {@code null}
     */
    public String header(String name) {
      return headers.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * @return the body as UTF-8 text
     */
    public String bodyText() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }

  /** A response. */
  public record Response(int status, Map<String, String> headers, String body) {

    /**
     * @param status the status
     * @param body an XML body
     * @return the response
     */
    public static Response xml(int status, String body) {
      return new Response(status, Map.of("Content-Type", "application/xml"), body);
    }

    /**
     * @param status the status
     * @return an empty response
     */
    public static Response empty(int status) {
      return new Response(status, Map.of(), "");
    }

    /**
     * @param name a header name
     * @param value its value
     * @return a copy with the header
     */
    public Response withHeader(String name, String value) {
      Map<String, String> copy = new LinkedHashMap<>(headers);
      copy.put(name, value);
      return new Response(status, copy, body);
    }
  }

  private final HttpServer server;
  private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());
  private volatile Function<RecordedRequest, Response> handler = request -> Response.empty(599);

  /** Starts the server on a free loopback port. */
  public FakeBlobServer() {
    try {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.createContext("/", this::handle);
    server.start();
  }

  /**
   * @param newHandler answers the requests
   */
  public void handler(Function<RecordedRequest, Response> newHandler) {
    this.handler = newHandler;
  }

  /**
   * @return the base URL ({@code http://127.0.0.1:<port>})
   */
  public String baseUrl() {
    return "http://" + server.getAddress().getAddress().getHostAddress() + ":" + server.getAddress().getPort();
  }

  /**
   * @param accountName a storage account
   * @return its path-style service URL on this server ({@code http://127.0.0.1:<port>/<account>})
   */
  public URI endpoint(String accountName) {
    return URI.create(baseUrl() + "/" + accountName);
  }

  /**
   * @return the recorded requests, in order
   */
  public List<RecordedRequest> requests() {
    synchronized (requests) {
      return List.copyOf(requests);
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    Map<String, String> headers = new LinkedHashMap<>();
    exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT),
        String.join(",", values)));
    byte[] body = exchange.getRequestBody().readAllBytes();
    RecordedRequest request = new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
        exchange.getRequestURI().getRawQuery(), headers, body);
    requests.add(request);
    Response response;
    try {
      response = handler.apply(request);
    } catch (RuntimeException e) {
      response = Response.empty(598);
    }
    byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
    response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
    exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    }
    exchange.close();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
