package com.boostercloud.rocket.webhook.support;

import com.boostercloud.framework.types.BoosterJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * An in-JVM HTTP server standing in for Cosmos DB in the Azure contract tests: it records every request and answers
 * transactional batches ({@code x-ms-cosmos-is-batch-request: True}) with one {@code 201} per operation, and anything
 * else with {@code 599} so an unexpected call fails loudly. It listens on a free port of the loopback interface.
 */
public final class FakeCosmosServer implements AutoCloseable {

  /** An account key in the format Cosmos DB uses (the well-known emulator key). */
  public static final String KEY =
      "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";

  /** A recorded request. */
  public record RecordedRequest(String method, String path, Map<String, String> headers, String body) {

    /**
     * @param name a header name (any case)
     * @return its value, or {@code null}
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

  private final HttpServer server;
  private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());

  /** Starts the server. */
  public FakeCosmosServer() {
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
   * @return {@code AccountEndpoint=http://127.0.0.1:<port>/;AccountKey=...;}
   */
  public String connectionString() {
    return "AccountEndpoint=http://" + server.getAddress().getAddress().getHostAddress() + ":"
        + server.getAddress().getPort() + "/;AccountKey=" + KEY + ";";
  }

  /**
   * @return the requests received so far, in order
   */
  public List<RecordedRequest> requests() {
    synchronized (requests) {
      return List.copyOf(requests);
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    Map<String, String> headers = new LinkedHashMap<>();
    exchange.getRequestHeaders().forEach((name, values) ->
        headers.put(name.toLowerCase(Locale.ROOT), String.join(",", values)));
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    RecordedRequest request = new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
        headers, body);
    requests.add(request);
    int status;
    String answer;
    if ("True".equalsIgnoreCase(request.header("x-ms-cosmos-is-batch-request"))) {
      List<Object> results = new ArrayList<>();
      for (Object operation : (List<?>) request.json()) {
        Map<?, ?> resource = (Map<?, ?>) ((Map<?, ?>) operation).get("resourceBody");
        results.add(Map.of("statusCode", 201, "requestCharge", 6.29, "resourceBody",
            Map.of("id", String.valueOf(resource.get("id")))));
      }
      status = 200;
      answer = BoosterJson.toJson(results);
    } else {
      status = 599;
      answer = "{\"code\":\"Unexpected\",\"message\":\"The fake Cosmos DB does not answer " + request.method() + " "
          + request.path() + "\"}";
    }
    byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
