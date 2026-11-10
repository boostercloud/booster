package com.boostercloud.framework.provider.azure.support;

import com.boostercloud.framework.types.BoosterJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * An in-JVM HTTP server ({@code com.sun.net.httpserver}) standing in for Cosmos DB, Event Hubs and Web PubSub in the
 * contract tests: it records every request exactly as received and answers with the responses queued by the test, in
 * order. An unexpected request (empty queue) is answered with 599 so the test fails loudly.
 */
public final class FakeAzureServer implements AutoCloseable {

  /** A recorded request. */
  public record RecordedRequest(String method, String path, String rawPath, String query, Map<String, String> headers,
      String body) {

    /**
     * @param name a header name (any case)
     * @return its first value, or {@code null}
     */
    public String header(String name) {
      return headers.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * @return the body parsed as JSON
     */
    public Object json() {
      return BoosterJson.parse(body);
    }
  }

  /** A response to send. */
  public record Response(int status, Map<String, String> headers, String body) {

    /**
     * @param status the status
     * @param body the body (JSON)
     * @return a JSON response
     */
    public static Response json(int status, String body) {
      return new Response(status, Map.of("Content-Type", "application/json"), body);
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
     * @return a copy with the header added
     */
    public Response withHeader(String name, String value) {
      Map<String, String> copy = new LinkedHashMap<>(headers);
      copy.put(name, value);
      return new Response(status, copy, body);
    }
  }

  private final HttpServer server;
  private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());
  private final Deque<Response> responses = new ArrayDeque<>();

  /** Starts a server on a random local port. */
  public FakeAzureServer() {
    try {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", this::handle);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.start();
  }

  /**
   * @return {@code http://127.0.0.1:<port>}
   */
  public String baseUrl() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
  }

  /**
   * @param response the next response
   * @return this server
   */
  public synchronized FakeAzureServer enqueue(Response response) {
    responses.addLast(response);
    return this;
  }

  /**
   * @return the requests received so far, in order
   */
  public List<RecordedRequest> requests() {
    synchronized (requests) {
      return List.copyOf(requests);
    }
  }

  /**
   * @param index the position
   * @return the request at that position
   */
  public RecordedRequest request(int index) {
    return requests().get(index);
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (InputStream input = exchange.getRequestBody()) {
      String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      Map<String, String> headers = new LinkedHashMap<>();
      exchange.getRequestHeaders().forEach((name, values) ->
          headers.put(name.toLowerCase(Locale.ROOT), values.isEmpty() ? "" : values.getFirst()));
      URI uri = exchange.getRequestURI();
      requests.add(new RecordedRequest(exchange.getRequestMethod(), uri.getPath(), uri.getRawPath(), uri.getRawQuery(),
          headers, body));
      Response response;
      synchronized (this) {
        response = responses.pollFirst();
      }
      if (response == null) {
        response = Response.json(599, "{\"message\":\"Unexpected request " + exchange.getRequestMethod() + " "
            + uri + "\"}");
      }
      response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
      byte[] bytes = response.body() == null ? new byte[0] : response.body().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
      if (bytes.length > 0) {
        try (OutputStream output = exchange.getResponseBody()) {
          output.write(bytes);
        }
      }
    } finally {
      exchange.close();
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
