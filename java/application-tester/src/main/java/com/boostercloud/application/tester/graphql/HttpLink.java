package com.boostercloud.application.tester.graphql;

import com.boostercloud.framework.types.BoosterJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * GraphQL over HTTP: Apollo's auth link + {@code HttpLink} with {@code cross-fetch}. Every operation is a
 * {@code POST} of {@code {"operationName", "variables", "query"}} with the headers {@code accept: *}{@code /*},
 * {@code content-type: application/json} and, when there is a token, {@code Authorization: Bearer <token>}.
 * Responses are checked like Apollo's {@code parseAndCheckHttpResponse}.
 */
final class HttpLink {

  private final HttpClient httpClient;
  private final String uri;
  private final AuthToken authToken;
  private final GraphQLClientOptions options;

  HttpLink(HttpClient httpClient, String uri, AuthToken authToken, GraphQLClientOptions options) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    this.uri = uri;
    this.authToken = Objects.requireNonNull(authToken, "authToken");
    this.options = Objects.requireNonNull(options, "options");
  }

  /** A request in flight. */
  record Pending(CompletableFuture<HttpResponse<String>> exchange, CompletableFuture<Object> result) {
    /** Aborts the request ({@code AbortController.abort()}). */
    void cancel() {
      exchange.cancel(true);
      result.cancel(true);
    }
  }

  /**
   * Sends the operation.
   *
   * @param operation the operation
   * @return the pending request; its result completes with the raw result, or exceptionally with a
   *     {@link ServerError}, {@link ServerParseError} or {@link FetchError}
   */
  Pending request(Operation operation) {
    HttpRequest request;
    try {
      request = buildRequest(operation);
    } catch (RuntimeException e) {
      CompletableFuture<Object> failed = CompletableFuture.failedFuture(new FetchError(uri, e));
      return new Pending(CompletableFuture.failedFuture(e), failed);
    }
    CompletableFuture<HttpResponse<String>> exchange =
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    CompletableFuture<Object> result = exchange.handle((response, error) -> {
      if (error != null) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        throw new CompletionException(new FetchError(uri, cause));
      }
      return parseAndCheck(operation, response.statusCode(), response.body());
    });
    return new Pending(exchange, result);
  }

  /**
   * Sends the operation and waits for the result.
   *
   * @param operation the operation
   * @return the raw result
   * @throws ServerError for bad statuses and results without {@code data}/{@code errors}
   * @throws ServerParseError for non-JSON bodies
   * @throws FetchError when the request cannot be performed
   */
  Object execute(Operation operation) {
    Pending pending = request(operation);
    try {
      return EventLoop.await(pending.result());
    } catch (RuntimeException e) {
      if (Thread.currentThread().isInterrupted()) {
        pending.cancel();
      }
      throw e;
    }
  }

  /**
   * @param operation the operation
   * @return the JSON body Apollo's {@code HttpLink} sends
   */
  Map<String, Object> body(Operation operation) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (operation.operationName() != null) {
      body.put("operationName", operation.operationName());
    }
    Map<String, Object> variables = operation.variables();
    if (!options.includeUnusedVariables() && !variables.isEmpty()) {
      Set<String> referenced = GraphQLDocument.parse(operation.query()).referencedVariables();
      if (!referenced.containsAll(variables.keySet())) {
        Map<String, Object> used = new LinkedHashMap<>(variables);
        used.keySet().retainAll(referenced);
        variables = used;
      }
    }
    body.put("variables", variables);
    body.put("query", operation.query());
    return body;
  }

  private HttpRequest buildRequest(Operation operation) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
        .header("accept", "*/*")
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(BoosterJson.toJson(body(operation))));
    authToken.authorization().ifPresent(authorization -> builder.header("Authorization", authorization));
    if (options.requestTimeout() != null) {
      builder.timeout(options.requestTimeout());
    }
    return builder.build();
  }

  /** {@code parseJsonBody} + {@code parseAndCheckHttpResponse}. */
  static Object parseAndCheck(Operation operation, int status, String bodyText) {
    if (status >= 300) {
      Object result;
      try {
        result = BoosterJson.parse(bodyText);
      } catch (RuntimeException e) {
        result = bodyText;
      }
      throw new CompletionException(new ServerError(
          "Response not successful: Received status code " + status, status, result, bodyText));
    }
    Object result;
    try {
      result = BoosterJson.parse(bodyText);
    } catch (RuntimeException e) {
      throw new CompletionException(new ServerParseError(e.getCause() != null ? e.getCause() : e, status, bodyText));
    }
    boolean hasResult = result instanceof List<?>
        || result instanceof Map<?, ?> map && (map.containsKey("data") || map.containsKey("errors"));
    if (!hasResult) {
      String name = operation.operationName() == null ? "undefined" : operation.operationName();
      throw new CompletionException(new ServerError(
          "Server response was missing for query '" + name + "'.", status, result, bodyText));
    }
    return result;
  }
}
