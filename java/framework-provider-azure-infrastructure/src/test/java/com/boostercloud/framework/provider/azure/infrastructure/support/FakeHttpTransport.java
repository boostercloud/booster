package com.boostercloud.framework.provider.azure.infrastructure.support;

import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Answers requests with scripted responses keyed by {@code "<METHOD> <url prefix>"} (the longest matching key wins;
 * queued responses are consumed in order and the last one repeats) and records every request.
 */
public final class FakeHttpTransport implements HttpTransport {

  /** The token response of the Entra ID endpoint. */
  public static final String TOKEN_RESPONSE = "{\"token_type\":\"Bearer\",\"expires_in\":3599,\"access_token\":\"arm-token\"}";

  private final List<Request> requests = new ArrayList<>();
  private final Map<String, Deque<Function<Request, Response>>> routes = new LinkedHashMap<>();

  /** Creates a transport answering the token endpoint. */
  public FakeHttpTransport() {
    on("POST https://login.microsoftonline.com/", Response.of(200, TOKEN_RESPONSE));
  }

  /**
   * @param route {@code "<METHOD> <url prefix>"}
   * @param responses the responses, in order (the last one repeats)
   * @return this transport
   */
  public synchronized FakeHttpTransport on(String route, Response... responses) {
    Deque<Function<Request, Response>> queue = new ArrayDeque<>();
    for (Response response : responses) {
      queue.add(request -> response);
    }
    routes.put(route, queue);
    return this;
  }

  /**
   * @param route {@code "<METHOD> <url prefix>"}
   * @param handler computes the response (may throw to simulate transport failures)
   * @return this transport
   */
  public synchronized FakeHttpTransport onRequest(String route, Function<Request, Response> handler) {
    Deque<Function<Request, Response>> queue = new ArrayDeque<>();
    queue.add(handler);
    routes.put(route, queue);
    return this;
  }

  /**
   * @param route {@code "<METHOD> <url prefix>"}
   * @return this transport, failing that route with a transport error
   */
  public synchronized FakeHttpTransport failing(String route) {
    return onRequest(route, request -> {
      throw new UncheckedIOException(new java.io.IOException("Connection refused"));
    });
  }

  @Override
  public Response send(Request request) {
    Function<Request, Response> handler;
    synchronized (this) {
      requests.add(request);
      String key = request.method() + " " + request.uri();
      String best = null;
      for (String route : routes.keySet()) {
        if (key.startsWith(route) && (best == null || route.length() > best.length())) {
          best = route;
        }
      }
      if (best == null) {
        return Response.of(404, "{\"error\":{\"code\":\"NotScripted\",\"message\":\"No response for " + key + "\"}}");
      }
      Deque<Function<Request, Response>> queue = routes.get(best);
      handler = queue.size() > 1 ? queue.poll() : queue.peek();
    }
    return handler.apply(request);
  }

  /**
   * @return every request, in order
   */
  public synchronized List<Request> requests() {
    return List.copyOf(requests);
  }

  /**
   * @return the requests other than the token requests, as {@code "<METHOD> <url>"}
   */
  public synchronized List<String> calls() {
    List<String> calls = new ArrayList<>();
    for (Request request : requests) {
      if (!request.uri().getHost().equals("login.microsoftonline.com")) {
        calls.add(request.method() + " " + request.uri());
      }
    }
    return calls;
  }

  /**
   * @param method the verb
   * @param urlPrefix the URL prefix
   * @return the matching requests
   */
  public synchronized List<Request> requests(String method, String urlPrefix) {
    List<Request> matches = new ArrayList<>();
    for (Request request : requests) {
      if (request.method().equals(method) && request.uri().toString().startsWith(urlPrefix)) {
        matches.add(request);
      }
    }
    return matches;
  }
}
