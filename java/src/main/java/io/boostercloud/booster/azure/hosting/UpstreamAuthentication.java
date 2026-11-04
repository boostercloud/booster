package io.boostercloud.booster.azure.hosting;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/** Shared-secret guard for TLS Azure service callbacks; user authentication remains separate. */
public final class UpstreamAuthentication implements HttpHandler {
  private final byte[] expected;
  private final HttpHandler next;
  public UpstreamAuthentication(String token, HttpHandler next) {
    if (token == null || token.length() < 32 || token.length() > 256)
      throw new IllegalArgumentException("Azure upstream token must contain 32–256 characters");
    expected = token.getBytes(StandardCharsets.UTF_8); this.next = Objects.requireNonNull(next);
  }
  public boolean accepts(URI uri) {
    String query = uri.getRawQuery();
    if (query == null || query.length() > 2048) return false;
    String found = null;
    try {
      for (String entry : query.split("&")) {
        String[] parts = entry.split("=", 2);
        if (URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals("code")) {
          if (found != null || parts.length < 2) return false;
          found = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }
      }
    } catch (IllegalArgumentException malformed) { return false; }
    return found != null && MessageDigest.isEqual(expected, found.getBytes(StandardCharsets.UTF_8));
  }
  /** Static secret header used by Event Grid delivery; always checked before parsing its body. */
  public static io.boostercloud.booster.transport.AuthenticationProvider bearer(String token) {
    if (token == null || token.length() < 32 || token.length() > 256) throw new IllegalArgumentException("Invalid Azure upstream token");
    byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    return header -> {
      if (header == null || !MessageDigest.isEqual(expected, header.getBytes(StandardCharsets.UTF_8)))
        throw new io.boostercloud.booster.transport.auth.AuthenticationException();
      return new io.boostercloud.booster.core.UserContext("azure-event-grid", java.util.Set.of("azure-service"), java.util.Map.of());
    };
  }
  @Override public void handle(HttpExchange exchange) throws IOException {
    if (!accepts(exchange.getRequestURI())) {
      try (exchange) { exchange.sendResponseHeaders(401, -1); }
      return;
    }
    next.handle(exchange);
  }
}
