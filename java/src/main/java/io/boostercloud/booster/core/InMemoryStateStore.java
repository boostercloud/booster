package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class InMemoryStateStore implements StateStore {
  private final Map<String, TreeMap<String, StateDocument>> namespaces = new HashMap<>();
  public synchronized Optional<StateDocument> get(String namespace, String key) {
    validate(namespace, key);
    return Optional.ofNullable(namespaces.getOrDefault(namespace, new TreeMap<>()).get(key));
  }
  public synchronized List<StateDocument> scan(String namespace, String afterKey, int limit) {
    validate(namespace, afterKey == null ? "_" : afterKey);
    if (limit < 1 || limit > 10000) throw new IllegalArgumentException("State page limit must be 1..10000");
    var map = namespaces.getOrDefault(namespace, new TreeMap<>());
    return (afterKey == null ? map : map.tailMap(afterKey, false)).values().stream().limit(limit).toList();
  }
  public synchronized StateDocument put(String namespace, String key, long expectedVersion, JsonNode value) {
    validate(namespace, key);
    if (expectedVersion < 0) throw new IllegalArgumentException("Negative state version");
    var map = namespaces.computeIfAbsent(namespace, unused -> new TreeMap<>());
    var previous = map.get(key);
    long actual = previous == null ? 0 : previous.version();
    if (actual != expectedVersion) throw new OptimisticConcurrencyException("State changed: " + namespace + "/" + key);
    var next = new StateDocument(namespace, key, Math.addExact(actual, 1), value);
    map.put(key, next); return next;
  }
  static void validate(String namespace, String key) {
    if (namespace == null || namespace.isBlank() || namespace.length() > 255 || key == null || key.isBlank() || key.length() > 2048)
      throw new IllegalArgumentException("Invalid state namespace or key");
  }
}
