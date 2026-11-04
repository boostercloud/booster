package io.boostercloud.booster.azure;

import com.azure.cosmos.CosmosContainer;
import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import java.util.*;

/** Durable snapshot, projection and job state with ETag compare-and-set and retained tombstones. */
public final class CosmosStateStore implements StateStore {
  private final CosmosDocuments documents;
  public CosmosStateStore(CosmosContainer container, String application) { this(new SdkCosmosDocuments(container, application)); }
  CosmosStateStore(CosmosDocuments documents) { this.documents = Objects.requireNonNull(documents); }
  @Override public Optional<StateDocument> get(String namespace, String key) {
    validate(namespace, key);
    return documents.read(id(namespace, key)).map(CosmosStateStore::decode);
  }
  @Override public List<StateDocument> scan(String namespace, String afterKey, int limit) {
    validate(namespace, afterKey == null ? "_" : afterKey);
    if (limit < 1 || limit > 10000) throw new IllegalArgumentException("State page limit must be 1..10000");
    String query = "SELECT * FROM c WHERE c.kind = 'state' AND c.namespace = @namespace";
    var parameters = new HashMap<String, Object>(); parameters.put("@namespace", namespace);
    if (afterKey != null) { query += " AND c.key > @after"; parameters.put("@after", afterKey); }
    query += " ORDER BY c.key";
    return documents.query(query, parameters, limit).stream().map(CosmosStateStore::decode).toList();
  }
  @Override public StateDocument put(String namespace, String key, long expectedVersion, JsonNode value) {
    validate(namespace, key); Objects.requireNonNull(value);
    if (expectedVersion < 0) throw new IllegalArgumentException("Negative state version");
    String id = id(namespace, key);
    var previous = documents.read(id);
    long actual = previous.map(CosmosStateStore::decode).map(StateDocument::version).orElse(0L);
    if (actual != expectedVersion) throw new OptimisticConcurrencyException("State changed: " + namespace + "/" + key);
    var next = new StateDocument(namespace, key, Math.addExact(actual, 1), value);
    var body = CosmosEventStore.object("state").put("namespace", namespace).put("key", key);
    body.set("state", Json.MAPPER.valueToTree(next));
    if (!documents.transact(List.of(new CosmosDocuments.Write(id, body, previous.map(CosmosDocuments.Document::etag).orElse(null)))))
      throw new OptimisticConcurrencyException("State changed: " + namespace + "/" + key);
    return next;
  }
  private static StateDocument decode(CosmosDocuments.Document document) { return CosmosEventStore.decode(document.value().get("state"), StateDocument.class); }
  private static String id(String namespace, String key) { return "state-" + CosmosEventStore.hash(Json.MAPPER.valueToTree(List.of(namespace, key)).toString()); }
  private static void validate(String namespace, String key) {
    if (namespace == null || namespace.isBlank() || namespace.length() > 255 || key == null || key.isBlank() || key.length() > 2048)
      throw new IllegalArgumentException("Invalid state namespace or key");
  }
}
