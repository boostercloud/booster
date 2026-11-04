package io.boostercloud.booster.core;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.*;
/** Ephemeral provider request. Never copied into event metadata or durable receipts. */
public record RequestData(Map<String,List<String>> headers, JsonNode body, Object rawContext) {
  public RequestData {
    var copy = new TreeMap<String,List<String>>(String.CASE_INSENSITIVE_ORDER);
    if (headers != null) headers.forEach((key, values) -> copy.put(key, List.copyOf(values)));
    headers = Collections.unmodifiableMap(copy); body = body == null ? NullNode.instance : body.deepCopy();
  }
  @Override public JsonNode body() { return body.deepCopy(); }
  public static RequestData empty() { return new RequestData(Map.of(), NullNode.instance, null); }
}
