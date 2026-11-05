package io.boostercloud.booster.rockets.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.transport.AuthenticationProvider;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * A native Java webhook dispatcher shared by the local server and Azure container host.
 * Handlers run as Booster actions: events and the response receipt commit together.
 */
public final class WebhookRocket {
  private static final Set<String> VOLATILE_HEADERS = Set.of("authorization", "proxy-authorization", "host",
      "connection", "content-length", "idempotency-key", "x-request-id", "user-agent", "accept-encoding");
  private final Booster booster;
  private final AuthenticationProvider authentication;
  private final Map<String, WebhookRoute> routes;
  private final int maxBodyBytes;
  private final int maxResponseBytes;
  private final boolean strictHttpSemantics;
  private final Semaphore inFlight;

  private WebhookRocket(Builder builder) {
    booster = builder.booster; authentication = builder.authentication;
    routes = Map.copyOf(builder.routes); maxBodyBytes = builder.maxBodyBytes;
    maxResponseBytes = builder.maxResponseBytes; strictHttpSemantics = builder.strictHttpSemantics;
    inFlight = new Semaphore(builder.maxConcurrentRequests);
  }
  public static Builder builder(Booster booster) { return new Builder(booster); }
  public Set<String> paths() { return routes.keySet(); }

  /** Mount at /webhook. Mount /api/webhook too only when a Functions-compatible prefix is required. */
  public HttpHandler handler() { return this::handle; }

  /** For an Azure/custom HTTP adapter; body limits and all authentication/authorization still apply. */
  public WebhookResponse dispatch(String method, URI uri, Map<String, List<String>> headers, byte[] body) {
    return dispatch(method, uri, headers, Map.of(), body);
  }

  public WebhookResponse dispatch(String method, URI uri, Map<String, List<String>> rawHeaders,
      Map<String, String> params, byte[] rawBody) {
    try {
      Objects.requireNonNull(uri); Objects.requireNonNull(method); Objects.requireNonNull(rawBody);
      if (uri.toASCIIString().length() > 8192) throw new WebhookInputException(414, "Webhook URI is too long");
      if (rawBody.length > maxBodyBytes) throw new WebhookInputException(413, "Request body too large");
      Map<String, List<String>> headers = normalizeHeaders(rawHeaders);
      String path = uri.getRawPath();
      if (path == null) throw new WebhookInputException(400, "Request URI must contain a path");
      if (path.startsWith("/api/")) path = path.substring(4);
      // Configured routes are literal. Encoded separators/dot segments never change route selection.
      WebhookRoute route = routes.get(path);
      if (route == null) return failure(strictHttpSemantics ? 404 : 500, "Error",
          "Could not find route " + path.replaceFirst("^/", "") + " in parameters.");
      if (!route.allowedMethods().contains(method)) {
        WebhookResponse response = failure(strictHttpSemantics ? 405 : 500, "Error", "Method " + method + " not allowed");
        return strictHttpSemantics ? response.withHeader("Allow", String.join(", ", new TreeSet<>(route.allowedMethods()))) : response;
      }
      String authorization = single(headers, "authorization");
      UserContext user = authentication.authenticate(authorization);
      if (user == null) throw new AuthenticationException();
      String requestId = single(headers, "idempotency-key");
      if (requestId == null) requestId = single(headers, "x-request-id");
      if (requestId == null) requestId = UUID.randomUUID().toString();
      if (!requestId.matches("[A-Za-z0-9._:-]{1,128}")) throw new WebhookInputException(400, "Invalid request identifier");
      String contentType = single(headers, "content-type");
      boolean multipart = contentType != null && contentType.split(";", 2)[0].trim().equalsIgnoreCase("multipart/form-data");
      String text = new String(rawBody, StandardCharsets.UTF_8);
      JsonNode value;
      if (multipart) value = Json.MAPPER.valueToTree(rawBody);
      else {
        try { value = Json.MAPPER.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(rawBody); }
        catch (IOException error) {
          if (strictHttpSemantics && contentType != null && contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
            throw new WebhookInputException(400, "Malformed JSON body");
          value = TextNode.valueOf(text);
        }
        if (value == null) value = TextNode.valueOf(text);
      }
      var request = new WebhookRequest(route.origin(), route.effectiveRoute(), strictHttpSemantics ? method : "POST", method,
          uri.toString(), uri.toString(), headers, query(uri.getRawQuery()), params, text, rawBody, value,
          multipart ? MultipartParser.parse(rawBody, contentType, route.multipart()) : null);
      if (route.requestAuthorizer() != null) route.requestAuthorizer().authorize(user, request);
      // Authentication credentials are never stored in the journal or used as a token-specific fingerprint.
      var fingerprintHeaders = new TreeMap<String, List<String>>(headers);
      VOLATILE_HEADERS.forEach(fingerprintHeaders::remove);
      var input = Json.MAPPER.createObjectNode();
      input.put("method", method); input.put("path", path); input.put("query", Objects.requireNonNullElse(uri.getRawQuery(), ""));
      input.put("body", Base64.getEncoder().encodeToString(rawBody));
      input.set("headers", Json.MAPPER.valueToTree(fingerprintHeaders)); input.set("params", Json.MAPPER.valueToTree(params));
      var context = new RequestContext("__booster_webhook:" + hash(route.effectiveRoute() + "\n" + requestId), user,
          Map.of("transport", "webhook", "route", route.effectiveRoute()));
      JsonNode result = booster.executeSystemAction("rocket.webhook:" + route.effectiveRoute(), input, context, route.authorize(), (ignored, register) -> {
        WebhookResponse response = route.handler().handle(request, register);
        if (response == null) response = WebhookResponse.empty();
        for (var header : register.responseHeaders().entrySet()) response = response.withHeader(header.getKey(), header.getValue());
        if (response.body().length > maxResponseBytes) throw new WebhookInputException(500, "Webhook response too large");
        return response;
      });
      WebhookResponse response = Json.MAPPER.treeToValue(result, WebhookResponse.class);
      return response.withHeader("X-Request-ID", requestId);
    } catch (AuthenticationException error) {
      return failure(401, "Not Authorized Error", "Invalid bearer token").withHeader("WWW-Authenticate", "Bearer");
    } catch (WebhookInputException error) {
      return failure(error.status, "Invalid Parameter Error", error.getMessage());
    } catch (BoosterException error) {
      return switch (error.code()) {
        case FORBIDDEN -> failure(strictHttpSemantics ? 403 : 401, "Not Authorized Error", error.getMessage());
        case VALIDATION -> failure(400, "Invalid Parameter Error", error.getMessage());
        case NOT_FOUND -> failure(404, "Not Found Error", error.getMessage());
        case CONFLICT -> failure(strictHttpSemantics ? 409 : 500, "Optimistic Concurrency Unexpected Version Error", error.getMessage());
        case INTERNAL -> failure(500, "Error", "Webhook handler failed");
      };
    } catch (IllegalArgumentException error) {
      return failure(400, "Invalid Parameter Error", "Invalid webhook request");
    } catch (Exception error) {
      return failure(500, "Error", "Webhook handler failed");
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    if (!inFlight.tryAcquire()) {
      try (exchange) { write(exchange, failure(503, "Error", "Webhook capacity reached")); }
      return;
    }
    try (exchange) {
      byte[] body = exchange.getRequestBody().readNBytes(maxBodyBytes + 1);
      write(exchange, dispatch(exchange.getRequestMethod(), exchange.getRequestURI(), exchange.getRequestHeaders(), body));
    } finally { inFlight.release(); }
  }
  private static void write(HttpExchange exchange, WebhookResponse response) throws IOException {
    response.headers().forEach((name, values) -> exchange.getResponseHeaders().put(name, values));
    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    byte[] body = response.body();
    boolean noBody = body.length == 0 || response.status() == 204 || response.status() == 304;
    exchange.sendResponseHeaders(response.status(), noBody ? -1 : body.length);
    if (!noBody) exchange.getResponseBody().write(body);
  }
  private static WebhookResponse failure(int status, String title, String reason) {
    WebhookResponse json = WebhookResponse.json(Map.of("title", title, "reason", Objects.requireNonNullElse(reason, "Request failed")));
    return new WebhookResponse(status, json.headers(), json.body());
  }
  private static String single(Map<String, List<String>> headers, String name) {
    List<String> values = headers.get(name);
    if (values == null || values.isEmpty()) return null;
    if (values.size() != 1) {
      if (name.equals("authorization")) throw new AuthenticationException();
      throw new WebhookInputException(400, "Repeated " + name + " header");
    }
    return values.getFirst();
  }
  private static Map<String, List<String>> normalizeHeaders(Map<String, List<String>> input) {
    var result = new TreeMap<String, List<String>>();
    int size = 0;
    for (var entry : input.entrySet()) {
      String name = entry.getKey().toLowerCase(Locale.ROOT);
      if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) throw new WebhookInputException(400, "Invalid header name");
      size += name.length();
      var values = result.computeIfAbsent(name, ignored -> new ArrayList<>());
      for (String value : entry.getValue()) {
        if (value.chars().anyMatch(c -> c == '\r' || c == '\n' || c == 0)) throw new WebhookInputException(400, "Invalid header value");
        size += value.length(); values.add(value);
      }
      if (size > 32768 || result.size() > 128) throw new WebhookInputException(431, "Request headers too large");
    }
    return WebhookRequest.immutable(result);
  }
  private static Map<String, List<String>> query(String raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    var values = new LinkedHashMap<String, List<String>>();
    String[] pairs = raw.split("&", -1);
    if (pairs.length > 128) throw new WebhookInputException(400, "Too many query parameters");
    for (String pair : pairs) {
      String[] parts = pair.split("=", 2);
      String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
      String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
      values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
    }
    return values;
  }
  private static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  public static final class Builder {
    private final Booster booster;
    private final Map<String, WebhookRoute> routes = new LinkedHashMap<>();
    private AuthenticationProvider authentication = AuthenticationProvider.anonymous();
    private int maxBodyBytes = 1_048_576;
    private int maxResponseBytes = 1_048_576;
    private int maxConcurrentRequests = 32;
    private boolean strictHttpSemantics;
    private Builder(Booster booster) { this.booster = Objects.requireNonNull(booster); }
    public Builder authentication(AuthenticationProvider provider) { authentication = Objects.requireNonNull(provider); return this; }
    public Builder route(WebhookRoute route) {
      if (routes.putIfAbsent(route.path(), route) != null) throw new IllegalArgumentException("Duplicate webhook route: " + route.path());
      return this;
    }
    public Builder route(String route, WebhookHandler handler) { return route(new WebhookRoute(route, handler)); }
    /** Opt-in corrections to the baseline's POST event method and generic method/route failure statuses. */
    public Builder strictHttpSemantics(boolean strict) { strictHttpSemantics = strict; return this; }
    public Builder limits(int bodyBytes, int responseBytes, int concurrentRequests) {
      if (bodyBytes < 0 || bodyBytes > 67_108_864 || responseBytes < 0 || responseBytes > 67_108_864
          || concurrentRequests < 1 || concurrentRequests > 1024) throw new IllegalArgumentException("Invalid webhook limits");
      maxBodyBytes = bodyBytes; maxResponseBytes = responseBytes; maxConcurrentRequests = concurrentRequests; return this;
    }
    public WebhookRocket build() {
      if (routes.isEmpty()) throw new IllegalArgumentException("At least one webhook route is required");
      return new WebhookRocket(this);
    }
  }
}
