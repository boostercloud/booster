package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;

public record PendingEvent(String id, StreamId stream, String type, int schemaVersion,
                           JsonNode payload, Instant timestamp, UserContext user,
                           Map<String, String> metadata) {
  public PendingEvent {
    if (id == null || id.isBlank() || stream == null || type == null || type.isBlank()
        || schemaVersion < 1 || payload == null || timestamp == null || user == null)
      throw new IllegalArgumentException("Invalid event envelope");
    payload = payload.deepCopy();
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
  @Override public JsonNode payload() { return payload.deepCopy(); }
  /** Logical payload redaction, retaining control descriptors needed to interpret the journal. */
  public PendingEvent redacted(Instant deletedAt) {
    var retained = new java.util.HashMap<>(metadata);
    retained.put("deletedAt", java.util.Objects.requireNonNull(deletedAt).toString());
    if (type.equals("__booster_schema_upgraded") && payload.isObject() && payload.has("state")) {
      var descriptor = (com.fasterxml.jackson.databind.node.ObjectNode) payload.deepCopy();
      descriptor.remove("state");
      retained.put("__booster_schemaDescriptor", descriptor.toString());
    }
    return new PendingEvent(id, stream, type, schemaVersion, Json.MAPPER.createObjectNode(), timestamp, user, retained);
  }
}
