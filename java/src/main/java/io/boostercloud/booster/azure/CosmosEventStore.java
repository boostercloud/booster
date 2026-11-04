package io.boostercloud.booster.azure;

import com.azure.cosmos.CosmosContainer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.boostercloud.booster.core.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Cosmos journal with atomic cross-stream commits within ONE application partition.
 * A conditional sequencer update serializes writers. No events are acknowledged before
 * the event documents, stream heads, receipt and sequencer commit as a single batch.
 * Cosmos's 100-operation / 2-MiB batch and 20-GiB logical-partition limits apply.
 */
public final class CosmosEventStore implements EventStore {
  private static final String POSITION = "journal-position";
  private static final int MAX_ATTEMPTS = 32;
  private final CosmosDocuments documents;

  public CosmosEventStore(CosmosContainer container, String application) {
    this(new SdkCosmosDocuments(container, application));
  }
  CosmosEventStore(CosmosDocuments documents) { this.documents = Objects.requireNonNull(documents); }

  @Override public Optional<CommandReceipt> findCommand(String requestId) {
    return documents.read(receiptId(requestId)).map(d -> decode(d.value().get("receipt"), CommandReceipt.class));
  }

  @Override public CommitResult commit(String requestId, String fingerprint, Map<StreamId, Long> expectedVersions,
      List<PendingEvent> events, JsonNode result) {
    return commitInternal(requestId, fingerprint, expectedVersions, events, result, null);
  }
  @Override public CommitResult commitAtPosition(String requestId, String fingerprint, Map<StreamId, Long> expectedVersions,
      List<PendingEvent> events, JsonNode result, long expectedPosition) {
    if (expectedPosition < 0) throw new IllegalArgumentException("Negative journal position");
    return commitInternal(requestId, fingerprint, expectedVersions, events, result, expectedPosition);
  }
  private CommitResult commitInternal(String requestId, String fingerprint, Map<StreamId, Long> expectedVersions,
      List<PendingEvent> events, JsonNode result, Long expectedPosition) {
    Objects.requireNonNull(requestId); Objects.requireNonNull(fingerprint); Objects.requireNonNull(result);
    expectedVersions = Map.copyOf(expectedVersions); events = List.copyOf(events);
    var ids = new HashSet<String>();
    for (var event : events) {
      if (!ids.add(event.id())) throw conflict("Duplicate event ID in batch");
      if (!expectedVersions.containsKey(event.stream())) throw new IllegalArgumentException("Every event stream needs an expected version");
    }
    if (expectedVersions.values().stream().anyMatch(v -> v < 0)) throw new IllegalArgumentException("Negative stream version");
    // One write per stream head, one per event, receipt and sequencer. Read-only stream
    // dependencies also have conditional replacements to preserve the checked versions.
    if (events.size() + expectedVersions.size() + 2 > 100)
      throw new BoosterException(BoosterException.Code.VALIDATION, "Cosmos transaction exceeds 100 document operations");
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      var existing = findCommand(requestId);
      if (existing.isPresent()) return duplicate(existing.get(), fingerprint);
      var positionDocument = documents.read(POSITION);
      long position = positionDocument.map(d -> d.value().path("position").asLong()).orElse(0L);
      if (expectedPosition != null && position != expectedPosition) {
        var raced = findCommand(requestId);
        if (raced.isPresent()) return duplicate(raced.get(), fingerprint);
        throw new OptimisticConcurrencyException("Journal position changed: expected " + expectedPosition + ", actual " + position);
      }
      var versions = new LinkedHashMap<StreamId, Long>();
      var heads = new LinkedHashMap<StreamId, Optional<CosmosDocuments.Document>>();
      for (var expected : expectedVersions.entrySet()) {
        var head = documents.read(streamId(expected.getKey())); heads.put(expected.getKey(), head);
        long actual = head.map(d -> d.value().path("version").asLong()).orElse(0L);
        if (actual != expected.getValue()) {
          // Another runtime may have completed this exact request between the reads.
          var raced = findCommand(requestId);
          if (raced.isPresent()) return duplicate(raced.get(), fingerprint);
          throw new OptimisticConcurrencyException(expected.getKey(), expected.getValue(), actual);
        }
        versions.put(expected.getKey(), actual);
      }
      var storedEvents = new ArrayList<StoredEvent>();
      var writes = new ArrayList<CosmosDocuments.Write>();
      for (var event : events) {
        if (documents.read(eventId(event.id())).isPresent()) {
          var raced = findCommand(requestId);
          if (raced.isPresent()) return duplicate(raced.get(), fingerprint);
          throw conflict("Event ID already exists");
        }
        long version = versions.compute(event.stream(), (key, value) -> value + 1);
        var stored = new StoredEvent(++position, version, event); storedEvents.add(stored);
        var data = object("event");
        data.put("position", position).put("streamVersion", version)
            .put("entityType", event.stream().entityType()).put("entityId", event.stream().entityId()).put("receiptId", receiptId(requestId));
        data.set("event", Json.MAPPER.valueToTree(stored));
        writes.add(new CosmosDocuments.Write(eventId(event.id()), data, null));
      }
      for (var entry : versions.entrySet()) {
        var data = object("stream").put("version", entry.getValue());
        writes.add(new CosmosDocuments.Write(streamId(entry.getKey()), data,
            heads.get(entry.getKey()).map(CosmosDocuments.Document::etag).orElse(null)));
      }
      var receipt = new CommandReceipt(requestId, fingerprint, result, storedEvents);
      var receiptData = object("receipt"); receiptData.set("receipt", Json.MAPPER.valueToTree(receipt));
      writes.add(new CosmosDocuments.Write(receiptId(requestId), receiptData, null));
      writes.add(new CosmosDocuments.Write(POSITION, object("position").put("position", position),
          positionDocument.map(CosmosDocuments.Document::etag).orElse(null)));
      // Keep headroom for protocol framing and partition/id properties added by the SDK adapter.
      long bytes = writes.stream().mapToLong(w -> w.value().toString().getBytes(StandardCharsets.UTF_8).length + 512L).sum();
      if (bytes > 1_900_000L)
        throw new BoosterException(BoosterException.Code.VALIDATION, "Cosmos transaction exceeds safe 2-MiB batch size");
      if (documents.transact(writes)) return new CommitResult(false, receipt);
    }
    throw conflict("Cosmos journal contention exceeded retry budget; retry with the same request ID");
  }

  @Override public List<StoredEvent> readStream(StreamId stream, long afterVersion) {
    if (afterVersion < 0) throw new IllegalArgumentException("Negative stream version");
    return documents.query("SELECT * FROM c WHERE c.kind = 'event' AND c.entityType = @type AND c.entityId = @id AND c.streamVersion > @after ORDER BY c.streamVersion",
        Map.of("@type", stream.entityType(), "@id", stream.entityId(), "@after", afterVersion), Integer.MAX_VALUE)
        .stream().map(d -> decode(d.value().get("event"), StoredEvent.class)).toList();
  }
  @Override public List<StoredEvent> readAll(long afterPosition, int limit) {
    if (afterPosition < 0 || limit < 1 || limit > 10000) throw new IllegalArgumentException("Invalid journal page");
    var result = documents.query("SELECT * FROM c WHERE c.kind = 'event' AND c.position > @after ORDER BY c.position",
        Map.of("@after", afterPosition), limit).stream().map(d -> decode(d.value().get("event"), StoredEvent.class)).toList();
    long expected = afterPosition + 1;
    for (var event : result) if (event.position() != expected++)
      throw new BoosterException(BoosterException.Code.INTERNAL, "Cosmos journal contains a gap; refusing to advance replay checkpoint");
    return result;
  }
  @Override public long lastPosition() { return documents.read(POSITION).map(d -> d.value().path("position").asLong()).orElse(0L); }
  @Override public boolean redactEventPayload(String eventId, java.time.Instant deletedAt) {
    Objects.requireNonNull(eventId); Objects.requireNonNull(deletedAt);
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      var existing = documents.read(eventId(eventId));
      if (existing.isEmpty()) return false;
      var stored = decode(existing.get().value().get("event"), StoredEvent.class);
      if (stored.event().metadata().containsKey("deletedAt") && stored.event().payload().isObject() && stored.event().payload().isEmpty()) return true;
      var metadata = new HashMap<>(stored.event().metadata()); metadata.putIfAbsent("deletedAt", deletedAt.toString());
      var original = stored.event();
      var redacted = new StoredEvent(stored.position(), stored.streamVersion(), new PendingEvent(original.id(), original.stream(),
          original.type(), original.schemaVersion(), Json.MAPPER.createObjectNode(), original.timestamp(), original.user(), metadata));
      var eventValue = (ObjectNode) existing.get().value(); eventValue.set("event", Json.MAPPER.valueToTree(redacted));
      var writes = new ArrayList<CosmosDocuments.Write>();
      writes.add(new CosmosDocuments.Write(existing.get().id(), eventValue, existing.get().etag()));
      String receiptId = existing.get().value().path("receiptId").asText();
      List<CosmosDocuments.Document> receipts = receiptId.isBlank()
          ? documents.query("SELECT DISTINCT VALUE c FROM c JOIN e IN c.receipt.events WHERE c.kind = 'receipt' AND e.event.id = @event", Map.of("@event", eventId), 100)
          : List.of(documents.read(receiptId).orElseThrow(() -> new IllegalStateException("Event has no owning receipt")));
      if (receipts.isEmpty()) throw new IllegalStateException("Event has no owning receipt");
      for (var document : receipts) {
        var receipt = decode(document.value().get("receipt"), CommandReceipt.class);
        var events = receipt.events().stream().map(event -> event.event().id().equals(eventId) ? redacted : event).toList();
        var value = (ObjectNode) document.value();
        value.set("receipt", Json.MAPPER.valueToTree(new CommandReceipt(receipt.requestId(), receipt.fingerprint(), receipt.result(), events)));
        writes.add(new CosmosDocuments.Write(document.id(), value, document.etag()));
      }
      if (documents.transact(writes)) return true;
    }
    throw conflict("Cosmos event redaction contention exceeded retry budget");
  }
  private static CommitResult duplicate(CommandReceipt receipt, String fingerprint) {
    if (!receipt.fingerprint().equals(fingerprint)) throw conflict("Request ID already belongs to another command");
    return new CommitResult(true, receipt);
  }
  static ObjectNode object(String kind) { return Json.MAPPER.createObjectNode().put("kind", kind); }
  static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
  }
  static String streamId(StreamId stream) { return "stream-" + hash(Json.MAPPER.valueToTree(stream).toString()); }
  static String eventId(String id) { return "event-" + hash(id); }
  static String receiptId(String id) { return "receipt-" + hash(id); }
  static <T> T decode(JsonNode value, Class<T> type) { return Json.MAPPER.convertValue(value, type); }
  static BoosterException conflict(String message) { return new BoosterException(BoosterException.Code.CONFLICT, message); }
}
