package io.boostercloud.booster.azure;

import java.util.*;

/** Transactional service boundary double, not an Azure emulator or cloud-readiness test. */
final class TestCosmosDocuments implements CosmosDocuments {
  final Map<String, Document> values = new HashMap<>();
  int rejectBatches;
  int batches;
  long revision;
  boolean failAfterCommit;
  @Override public synchronized Optional<Document> read(String id) { return Optional.ofNullable(values.get(id)); }
  @Override public synchronized boolean transact(List<Write> writes) {
    batches++;
    if (rejectBatches-- > 0) return false;
    var ids = new HashSet<String>();
    for (var write : writes) {
      if (!ids.add(write.id())) return false;
      var existing = values.get(write.id());
      if (write.expectedEtag() == null ? existing != null : existing == null || !write.expectedEtag().equals(existing.etag())) return false;
    }
    for (var write : writes) values.put(write.id(), new Document(write.id(), write.value(), Long.toString(++revision)));
    if (failAfterCommit) { failAfterCommit = false; throw new IllegalStateException("Simulated lost service acknowledgement"); }
    return true;
  }
  @Override public synchronized List<Document> query(String sql, Map<String, Object> p, int limit) {
    var result = new ArrayList<Document>();
    for (var document : values.values()) {
      var v = document.value();
      if (sql.contains("kind = 'event'") && !v.path("kind").asText().equals("event")) continue;
      if (sql.contains("kind = 'state'") && !v.path("kind").asText().equals("state")) continue;
      if (sql.contains("kind = 'subscription'") && !v.path("kind").asText().equals("subscription")) continue;
      if (sql.contains("kind = 'connection'") && !v.path("kind").asText().equals("connection")) continue;
      if (p.containsKey("@type") && !p.get("@type").equals(v.path("entityType").asText())) continue;
      if (p.containsKey("@id") && !p.get("@id").equals(v.path("entityId").asText())) continue;
      if (p.containsKey("@namespace") && !p.get("@namespace").equals(v.path("namespace").asText())) continue;
      if (p.containsKey("@connection") && !p.get("@connection").equals(v.path("connectionId").asText())) continue;
      if (p.containsKey("@now")) {
        long now = ((Number) p.get("@now")).longValue(), expires = v.path("expires").asLong();
        if (sql.contains("c.expires <= @now") ? expires <= 0 || expires > now : expires <= now) continue;
      }
      if (p.containsKey("@after")) {
        if (sql.contains("c.key >")) { if (v.path("key").asText().compareTo((String) p.get("@after")) <= 0) continue; }
        else {
          String key = sql.contains("c.streamVersion >") ? "streamVersion" : "position";
          if (v.path(key).asLong() <= ((Number) p.get("@after")).longValue()) continue;
        }
      }
      result.add(document);
    }
    if (sql.contains("ORDER BY c.key")) result.sort(Comparator.comparing(d -> d.value().path("key").asText()));
    else if (sql.contains("ORDER BY c.streamVersion")) result.sort(Comparator.comparingLong(d -> d.value().path("streamVersion").asLong()));
    else if (sql.contains("ORDER BY c.position")) result.sort(Comparator.comparingLong(d -> d.value().path("position").asLong()));
    return result.stream().limit(limit).toList();
  }
}
