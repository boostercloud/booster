package io.boostercloud.booster.rockets.webhook;

import io.boostercloud.booster.core.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Serialized in the command receipt so idempotent delivery returns the original response. */
public record WebhookResponse(int status, Map<String, List<String>> headers, byte[] body) {
  private static final Set<String> RESERVED = Set.of("content-length", "transfer-encoding", "connection",
      "keep-alive", "trailer", "upgrade", "proxy-authenticate", "proxy-authorization", "te");
  public WebhookResponse {
    if (status < 200 || status > 599) throw new IllegalArgumentException("Invalid response status");
    var normalized = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
    normalized.put("Access-Control-Allow-Origin", List.of("*"));
    if (headers != null) headers.forEach((name, values) -> {
      if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || RESERVED.contains(name.toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Invalid response header");
      for (String value : values) if (value == null || value.chars().anyMatch(c -> c == '\r' || c == '\n' || c == 0))
        throw new IllegalArgumentException("Invalid response header value");
      normalized.put(name, List.copyOf(values));
    });
    headers = Collections.unmodifiableMap(normalized);
    body = body == null ? new byte[0] : body.clone();
  }
  @Override public byte[] body() { return body.clone(); }
  public static WebhookResponse empty() { return text(""); }
  public static WebhookResponse text(String value) {
    return new WebhookResponse(200, Map.of("Content-Type", List.of("text/plain")),
        Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8));
  }
  public static WebhookResponse json(Object value) {
    try {
      // The existing response builder returns an empty body for JS-falsy values.
      byte[] bytes = falsy(value) ? new byte[0] : Json.MAPPER.writeValueAsBytes(value);
      return new WebhookResponse(200, Map.of("Content-Type", List.of("application/json")), bytes);
    } catch (Exception error) { throw new IllegalArgumentException("Response cannot be serialized", error); }
  }
  public static WebhookResponse file(String name, String mimeType, byte[] bytes) {
    Objects.requireNonNull(name); Objects.requireNonNull(mimeType);
    if (name.chars().anyMatch(c -> c < 32 || c == 127)) throw new IllegalArgumentException("Invalid filename");
    return new WebhookResponse(200, Map.of("Content-Type", List.of(mimeType),
        "Content-Disposition", List.of("attachment; filename=" + name)), bytes);
  }
  public WebhookResponse withHeader(String name, String value) {
    var updated = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER); updated.putAll(headers);
    updated.put(name, List.of(value)); return new WebhookResponse(status, updated, body);
  }
  private static boolean falsy(Object value) {
    if (value instanceof JsonNode node) {
      if (node.isNull() || node.isMissingNode()) return true;
      if (node.isBoolean()) return !node.asBoolean();
      if (node.isNumber()) return node.asDouble() == 0 || Double.isNaN(node.asDouble());
      return node.isTextual() && node.asText().isEmpty();
    }
    return value == null || Boolean.FALSE.equals(value) || "".equals(value)
        || value instanceof Number number && (number.doubleValue() == 0d || Double.isNaN(number.doubleValue()));
  }
}
