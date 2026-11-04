package io.boostercloud.booster.rockets.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.*;

/** A provider-neutral request, including the original binary body and the baseline event fields. */
public record WebhookRequest(String origin, String route, String method, String httpMethod,
    String url, String originalUrl, Map<String, List<String>> headers,
    Map<String, List<String>> query, Map<String, String> params,
    String rawBody, byte[] rawBytes, JsonNode body, MultipartForm multipart) {
  public WebhookRequest {
    headers = immutable(headers); query = immutable(query); params = Map.copyOf(params);
    rawBytes = rawBytes.clone(); body = body == null ? NullNode.instance : body.deepCopy();
  }
  @Override public byte[] rawBytes() { return rawBytes.clone(); }
  @Override public JsonNode body() { return body.deepCopy(); }
  static Map<String, List<String>> immutable(Map<String, List<String>> values) {
    var result = new LinkedHashMap<String, List<String>>();
    values.forEach((key, value) -> result.put(key, List.copyOf(value)));
    return Collections.unmodifiableMap(result);
  }
}
