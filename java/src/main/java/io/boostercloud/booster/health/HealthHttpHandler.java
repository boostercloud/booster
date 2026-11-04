package io.boostercloud.booster.health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.boostercloud.booster.core.BoosterException;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.transport.AuthenticationProvider;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** GET /health and /health/component/path, returning the baseline component array. */
public final class HealthHttpHandler implements HttpHandler {
  private final HealthService service;
  private final AuthenticationProvider authentication;
  public HealthHttpHandler(HealthService service, AuthenticationProvider authentication) {
    this.service = Objects.requireNonNull(service); this.authentication = Objects.requireNonNull(authentication);
  }
  @Override public void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      try {
        if (!exchange.getRequestMethod().equals("GET")) {
          exchange.getResponseHeaders().set("Allow", "GET"); respond(exchange, 405, Map.of("error", "Method not allowed")); return;
        }
        String path = exchange.getRequestURI().getPath();
        if (!path.equals("/health") && !path.startsWith("/health/")) { respond(exchange, 404, Map.of("error", "Not found")); return; }
        List<String> credentials = exchange.getRequestHeaders().get("Authorization");
        if (credentials != null && credentials.size() != 1) throw new AuthenticationException();
        var user = authentication.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
        String component = path.equals("/health") ? "" : path.substring("/health/".length());
        var report = service.check(new HealthRequest(component, user, Map.of("transport", "http", "method", "GET")));
        respond(exchange, report.healthy() ? 200 : 503, report.components());
      } catch (AuthenticationException failure) {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer"); respond(exchange, 401, Map.of("error", "Invalid bearer token"));
      } catch (BoosterException failure) {
        int status = switch (failure.code()) { case FORBIDDEN -> 403; case NOT_FOUND -> 404; case VALIDATION -> 400; default -> 503; };
        respond(exchange, status, Map.of("error", status == 403 ? "Health access is not authorized" : "Health request failed"));
      } catch (IllegalArgumentException failure) { respond(exchange, 400, Map.of("error", "Invalid health component")); }
      catch (RuntimeException failure) { respond(exchange, 503, Map.of("error", "Health service unavailable")); }
    }
  }
  private static void respond(HttpExchange exchange, int status, Object value) throws IOException {
    byte[] body = Json.MAPPER.writeValueAsBytes(value);
    if (body.length > 1_048_576) { status = 503; body = "{\"error\":\"Health response exceeds limit\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8); }
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
  }
}
