package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record CommandReceipt(String requestId, String fingerprint, JsonNode result, List<StoredEvent> events) {
  public CommandReceipt { result = result.deepCopy(); events = List.copyOf(events); }
  @Override public JsonNode result() { return result.deepCopy(); }
}
