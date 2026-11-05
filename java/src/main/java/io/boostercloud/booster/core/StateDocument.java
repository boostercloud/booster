package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** Versioned provider document. JSON null is a retained tombstone, never a missing revision. */
public record StateDocument(String namespace, String key, long version, JsonNode value) {
  public StateDocument {
    if (namespace == null || namespace.isBlank() || key == null || key.isBlank() || version < 1)
      throw new IllegalArgumentException("State namespace, key and positive version are required");
    value = Objects.requireNonNull(value).deepCopy();
  }
  @Override public JsonNode value() { return value.deepCopy(); }
  public boolean deleted() { return value.isNull(); }
}
