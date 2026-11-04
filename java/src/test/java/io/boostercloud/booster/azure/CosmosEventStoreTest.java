package io.boostercloud.booster.azure;

import io.boostercloud.booster.core.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CosmosEventStoreTest {
  static PendingEvent event(String id, StreamId stream) {
    return new PendingEvent(id, stream, "Changed", 1, Json.MAPPER.createObjectNode().put("id", stream.entityId()),
        Instant.parse("2026-01-01T00:00:00Z"), UserContext.anonymous(), Map.of("correlationId", "test"));
  }
  @Test void atomicMultiStreamReceiptAndReopen() {
    var data = new TestCosmosDocuments(); var store = new CosmosEventStore(data);
    var a = new StreamId("Account", "a"); var b = new StreamId("Account", "b");
    var result = store.commit("request", "fp", Map.of(a, 0L, b, 0L), List.of(event("1", a), event("2", b), event("3", a)), Json.MAPPER.valueToTree(Map.of("ok", true)));
    assertFalse(result.duplicate()); assertEquals(3, store.lastPosition());
    assertEquals(List.of(1L, 2L), store.readStream(a, 0).stream().map(StoredEvent::streamVersion).toList());
    assertEquals(List.of(2L, 3L), store.readAll(1, 2).stream().map(StoredEvent::position).toList());
    var restarted = new CosmosEventStore(data);
    assertTrue(restarted.commit("request", "fp", Map.of(a, 0L), List.of(event("1", a)), Json.MAPPER.nullNode()).duplicate());
    assertEquals(result.receipt(), restarted.findCommand("request").orElseThrow());
    assertThrows(BoosterException.class, () -> restarted.commit("request", "changed", Map.of(), List.of(), Json.MAPPER.nullNode()));
    assertEquals(3, restarted.lastPosition());
  }
  @Test void conflictsLeaveNoPartialWrites() {
    var data = new TestCosmosDocuments(); var store = new CosmosEventStore(data);
    var a = new StreamId("Account", "a"); var b = new StreamId("Account", "b");
    store.commit("first", "fp", Map.of(a, 0L), List.of(event("1", a)), Json.MAPPER.nullNode());
    int size = data.values.size();
    assertThrows(OptimisticConcurrencyException.class, () -> store.commit("bad", "fp", Map.of(a, 0L, b, 0L), List.of(event("2", b)), Json.MAPPER.nullNode()));
    assertEquals(size, data.values.size()); assertTrue(store.findCommand("bad").isEmpty()); assertTrue(store.readStream(b, 0).isEmpty());
  }
  @Test void retriesCosmosPreconditionsAndRecoversLostAcknowledgement() {
    var data = new TestCosmosDocuments(); data.rejectBatches = 3;
    var store = new CosmosEventStore(data); var a = new StreamId("A", "a");
    store.commit("r", "fp", Map.of(a, 0L), List.of(event("e", a)), Json.MAPPER.nullNode());
    assertEquals(4, data.batches); assertEquals(1, store.lastPosition());
    data.failAfterCommit = true;
    assertThrows(IllegalStateException.class, () -> store.commit("r2", "fp2", Map.of(a, 1L), List.of(event("e2", a)), Json.MAPPER.nullNode()));
    assertTrue(store.commit("r2", "fp2", Map.of(a, 1L), List.of(event("e2", a)), Json.MAPPER.nullNode()).duplicate());
    assertEquals(2, store.lastPosition());
  }
  @Test void concurrentDifferentStreamsHaveConsecutiveGlobalPositions() throws Exception {
    var data = new TestCosmosDocuments();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new ArrayList<Future<?>>();
      for (int i = 0; i < 8; i++) {
        int id = i;
        futures.add(executor.submit(() -> {
          var store = new CosmosEventStore(data); var stream = new StreamId("A", Integer.toString(id));
          for (int n = 0; n < 10; n++) store.commit(id + ":" + n, "fp", Map.of(stream, (long) n), List.of(event(id + ":" + n, stream)), Json.MAPPER.nullNode());
        }));
      }
      for (var future : futures) future.get(10, TimeUnit.SECONDS);
    }
    var store = new CosmosEventStore(data); assertEquals(80, store.lastPosition()); assertEquals(80, store.readAll(0, 100).size());
  }
  @Test void duplicateEventIdsAndServiceLimitsFailBeforeWriting() {
    var data = new TestCosmosDocuments(); var store = new CosmosEventStore(data); var a = new StreamId("A", "a");
    assertThrows(BoosterException.class, () -> store.commit("r", "fp", Map.of(a, 0L), List.of(event("e", a), event("e", a)), Json.MAPPER.nullNode()));
    var many = java.util.stream.IntStream.range(0, 98).mapToObj(i -> event("event" + i, a)).toList();
    assertThrows(BoosterException.class, () -> store.commit("r", "fp", Map.of(a, 0L), many, Json.MAPPER.nullNode()));
    assertTrue(data.values.isEmpty());
    store.commit("r", "fp", Map.of(a, 0L), List.of(event("e", a)), Json.MAPPER.nullNode());
    assertThrows(BoosterException.class, () -> store.commit("r2", "fp", Map.of(a, 1L), List.of(event("e", a)), Json.MAPPER.nullNode()));
    assertEquals(1, store.lastPosition());
  }
  @Test void eventlessCommandsAreDurableAndDefensivelyCopied() {
    var store = new CosmosEventStore(new TestCosmosDocuments()); var json = Json.MAPPER.createObjectNode().put("result", 1);
    store.commit("empty", "fp", Map.of(), List.of(), json); json.put("result", 2);
    assertEquals(1, store.findCommand("empty").orElseThrow().result().path("result").asInt()); assertEquals(0, store.lastPosition());
  }
  @Test void payloadRedactionUpdatesJournalAndReceiptAtomicallyAndPreservesPositions() {
    var data = new TestCosmosDocuments(); var store = new CosmosEventStore(data); var a = new StreamId("A", "a");
    store.commit("r", "fp", Map.of(a, 0L), List.of(event("secret", a)), Json.MAPPER.valueToTree("independent result"));
    data.rejectBatches = 2;
    Instant deletedAt = Instant.parse("2026-02-01T00:00:00Z");
    assertTrue(store.redactEventPayload("secret", deletedAt));
    var redacted = store.readAll(0, 10).getFirst();
    assertTrue(redacted.event().payload().isEmpty()); assertEquals(deletedAt.toString(), redacted.event().metadata().get("deletedAt"));
    assertEquals(1, redacted.position()); assertEquals(1, redacted.streamVersion()); assertEquals(1, store.lastPosition());
    assertEquals(redacted, store.findCommand("r").orElseThrow().events().getFirst());
    assertEquals("independent result", store.findCommand("r").orElseThrow().result().asText());
    assertTrue(store.redactEventPayload("secret", deletedAt.plusSeconds(10)));
    assertEquals(deletedAt.toString(), store.readAll(0, 10).getFirst().event().metadata().get("deletedAt"));
    assertFalse(store.redactEventPayload("absent", deletedAt));
  }
  @Test void globalPositionFenceRejectsStaleCrossStreamValidationButReplaysReceipt() {
    var store = new CosmosEventStore(new TestCosmosDocuments()); var a = new StreamId("A", "a"); var b = new StreamId("A", "b");
    store.commitAtPosition("first", "fp", Map.of(a, 0L), List.of(event("a1", a)), Json.MAPPER.nullNode(), 0);
    assertThrows(OptimisticConcurrencyException.class, () -> store.commitAtPosition("stale", "fp", Map.of(b, 0L), List.of(event("b1", b)), Json.MAPPER.nullNode(), 0));
    assertTrue(store.findCommand("stale").isEmpty()); assertEquals(1, store.lastPosition());
    assertTrue(store.commitAtPosition("first", "fp", Map.of(a, 0L), List.of(event("a1", a)), Json.MAPPER.nullNode(), 0).duplicate());
    store.commitAtPosition("second", "fp", Map.of(b, 0L), List.of(event("b1", b)), Json.MAPPER.nullNode(), 1);
    assertEquals(2, store.lastPosition());
  }
  @Test void redactingSchemaAnchorRetainsOnlyDescriptorWithoutSnapshotState() throws Exception {
    var store = new CosmosEventStore(new TestCosmosDocuments()); var stream = new StreamId("Account", "a");
    var payload = Json.MAPPER.createObjectNode().put("fromVersion", 1).put("toVersion", 2).put("sourcePosition", 10);
    payload.set("state", Json.MAPPER.createObjectNode().put("id", "a").put("privateValue", "must-be-redacted"));
    var anchor = new PendingEvent("anchor", stream, "__booster_schema_upgraded", 1, payload,
        Instant.parse("2026-01-01T00:00:00Z"), UserContext.anonymous(), Map.of());
    store.commit("anchor-command", "fp", Map.of(stream, 0L), List.of(anchor), Json.MAPPER.nullNode());
    store.redactEventPayload("anchor", Instant.parse("2026-02-01T00:00:00Z"));
    var event = store.readAll(0, 10).getFirst().event();
    assertTrue(event.payload().isEmpty());
    var descriptor = Json.MAPPER.readTree(event.metadata().get("__booster_schemaDescriptor"));
    assertFalse(descriptor.has("state")); assertEquals(2, descriptor.path("toVersion").asInt());
    assertFalse(Json.MAPPER.writeValueAsString(event).contains("must-be-redacted"));
    assertEquals(event, store.findCommand("anchor-command").orElseThrow().events().getFirst().event());
  }
}
