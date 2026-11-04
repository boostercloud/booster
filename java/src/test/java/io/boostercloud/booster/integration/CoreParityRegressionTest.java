package io.boostercloud.booster.integration;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.store.JdbcEventStore;
import io.boostercloud.booster.store.JdbcStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Independent regressions for cross-runtime races and persisted schema identities. */
class CoreParityRegressionTest {
  @TempDir Path directory;
  record Counter(String id, int total) {}
  record CounterView(String id, int total) {}
  record Added(String id, int amount) {}
  record Owner(String id) {}
  record Dependent(String id) {}
  record OwnerCreated(String id) {}
  record DependentCreated(String id) {}
  record SharedView(String id, int references) {}
  record RevisionEntity(String id, int revision, int value) {}
  record RevisionView(String id, int revision, int value) {}
  record RevisionWritten(String id, int revision, int value) {}
  record NumericSource(String id, Number revision, int value) {}
  record NumericWritten(String id, Number revision, int value) {}
  record NumericView(String id, Number revision, int total) {}

  private static RequestContext request(String id) { return new RequestContext(id, UserContext.anonymous(), Map.of()); }
  private static JsonNode json(Object value) { return Json.MAPPER.valueToTree(value); }
  private static Booster.Builder counter(EventStore events, StateStore state) {
    return Booster.builder(events).stateStore(state).onError(error -> {})
        .event(Added.class, Added::id, Counter.class,
            (event, previous) -> new Counter(event.id(), (previous == null ? 0 : previous.total()) + event.amount()))
        .readModel(CounterView.class, Counter.class, AccessPolicy.publicAccess(),
            (entity, previous) -> entity == null ? null : new CounterView(entity.id(), entity.total()))
        .eventAdministrationAccess(Counter.class, AccessPolicy.publicAccess())
        .eventHistoryAccess(Counter.class, AccessPolicy.publicAccess());
  }
  private static void add(Booster runtime, int amount, RequestContext context) {
    runtime.executeAction("add", json(new Added("a", amount)), context, AccessPolicy.publicAccess(),
        (input, register) -> { register.emit(Json.MAPPER.treeToValue(input, Added.class)); return null; });
  }

  @Test void acknowledgedStateCheckpointSurvivesAbruptWorkerDeath() throws Exception {
    Path database = directory.resolve("crash-state");
    var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp", System.getProperty("java.class.path"), StateWriter.class.getName(), database.toString())
        .redirectErrorStream(true).start();
    try {
      var acknowledged = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
        try (var output = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
          String line;
          while ((line = output.readLine()) != null) if (line.equals("COMMITTED")) return true;
          return false;
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
      });
      assertTrue(acknowledged.get(10, TimeUnit.SECONDS));
      process.destroyForcibly(); assertTrue(process.waitFor(10, TimeUnit.SECONDS));
      try (var reopened = new JdbcStateStore(database)) {
        assertEquals(123, reopened.get("migration", "checkpoint").orElseThrow().value().path("position").asInt());
      }
    } finally { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
  }

  public static final class StateWriter {
    public static void main(String[] arguments) throws Exception {
      var state = new JdbcStateStore(Path.of(arguments[0]));
      state.put("migration", "checkpoint", 0, Json.MAPPER.createObjectNode().put("position", 123));
      System.out.println("COMMITTED"); System.out.flush();
      new CountDownLatch(1).await();
    }
  }

  @Test void existingDomainMetadataDoesNotPreventActualEventPayloadRedaction() {
    try (var runtime = counter(JdbcEventStore.inMemory(), JdbcStateStore.inMemory()).build()) {
      add(runtime, 7, new RequestContext("add", UserContext.anonymous(), Map.of("deletedAt", "domain-supplied-value")));
      assertEquals(1, runtime.searchEvents("Counter", null, "a", null, null, 10, request("history")).size(),
          "Domain metadata cannot hide an event without a committed deletion marker");
      var event = runtime.eventStore().readStream(new StreamId("Counter", "a"), 0).getFirst().event();
      assertTrue(runtime.deleteEvent("Counter", "a", event.timestamp(), request("delete")));
      var deleted = runtime.eventStore().readStream(new StreamId("Counter", "a"), 0).getFirst().event();
      assertTrue(deleted.payload().isObject() && deleted.payload().isEmpty(), "Deletion must clear the payload even when metadata already contains deletedAt");
      assertTrue(runtime.eventStore().findCommand("add").orElseThrow().events().getFirst().event().payload().isEmpty());
    }
  }

  @Test void staleRuntimeCannotDowngradePersistedDerivedSchemaAfterAnotherRuntimeUpgradesIt() {
    try (var old = counter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
      add(old, 1, request("initial"));
      try (var upgraded = counter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state")))
          .schemaVersion(Counter.class, 2).upcaster(Counter.class, 1, value -> value)
          .schemaVersion(CounterView.class, 2).upcaster(CounterView.class, 1, value -> value).build()) {
        assertEquals(2, upgraded.stateStore().get("snapshots:Counter", "a").orElseThrow().value().path("schemaVersion").asInt());
        try { add(old, 1, request("old-worker")); }
        catch (BoosterException rejected) { assertNotEquals(BoosterException.Code.FORBIDDEN, rejected.code()); }
        assertEquals(2, upgraded.stateStore().get("snapshots:Counter", "a").orElseThrow().value().path("schemaVersion").asInt(), "An older worker cannot replace a newer cache schema");
        assertEquals(2, upgraded.stateStore().get("readModels:CounterView", "a").orElseThrow().value().path("schemaVersion").asInt());
      }
    }
  }

  private static Booster.Builder migratedCounter(EventStore events, StateStore state) {
    return migratedCounter(events, state, 2);
  }

  private static Booster.Builder migratedCounter(EventStore events, StateStore state, int modelMultiplier) {
    return counter(events, state)
        .schemaVersion(Counter.class, 2).upcaster(Counter.class, 1,
            value -> ((com.fasterxml.jackson.databind.node.ObjectNode) value).put("total", value.path("total").asInt() * 2))
        .schemaVersion(CounterView.class, 2).upcaster(CounterView.class, 1,
            value -> ((com.fasterxml.jackson.databind.node.ObjectNode) value).put("total", value.path("total").asInt() * modelMultiplier));
  }

  private static void assertCounter(Booster runtime, int total) {
    assertEquals(total, runtime.entity(Counter.class, "a").total());
    assertEquals(total, runtime.getReadModel("CounterView", "a", request("read")).path("total").asInt());
  }

  @Test void migratedSnapshotAndReadModelValuesAreUsedByRuntimeReads() throws Exception {
    try (var runtime = counter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
      add(runtime, 10, request("initial"));
    }
    try (var migrated = migratedCounter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
      assertEquals(20, migrated.stateStore().get("snapshots:Counter", "a").orElseThrow().value().path("state").path("total").asInt());
      assertEquals(20, migrated.entity(Counter.class, "a").total(), "Entity reads must use the migrated snapshot value");
      assertEquals(20, migrated.getReadModel("CounterView", "a", request("read")).path("total").asInt(),
          "Read-model reads must use their migrated value");
      add(migrated, 5, request("after-migration"));
      assertEquals(25, migrated.entity(Counter.class, "a").total(), "Later events must reduce against the migrated entity");
      assertEquals(25, migrated.getReadModel("CounterView", "a", request("read-after")).path("total").asInt());
      migrated.rebuild();
      assertCounter(migrated, 25);
    }
    var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp", System.getProperty("java.class.path"), MigratedCounterReader.class.getName(), directory.toString())
        .redirectErrorStream(true).start();
    try {
      assertTrue(process.waitFor(20, TimeUnit.SECONDS));
      String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      assertEquals(0, process.exitValue(), output);
      assertTrue(output.contains("MIGRATED_RESTART_OK"), output);
    } finally { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
  }

  public static final class MigratedCounterReader {
    public static void main(String[] arguments) {
      Path directory = Path.of(arguments[0]);
      try (var runtime = migratedCounter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
        assertCounter(runtime, 25);
        runtime.rebuild(); assertCounter(runtime, 25);
        System.out.println("MIGRATED_RESTART_OK");
      }
    }
  }

  @Test void deletingHistoricalEventInvalidatesMigratedCopiesWithoutResurrectingTheirValues() {
    java.time.Instant timestamp;
    try (var runtime = counter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
      add(runtime, 10, request("original"));
      timestamp = runtime.history(Counter.class, "a").getFirst().event().timestamp();
      add(runtime, 5, request("surviving"));
    }
    try (var migrated = migratedCounter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
      assertCounter(migrated, 30);
      add(migrated, 7, request("later")); assertCounter(migrated, 37);
      assertTrue(migrated.deleteEvent("Counter", "a", timestamp, request("delete-original")));
      assertCounter(migrated, 12);
      assertInvalidatedSchemaCopiesRedacted(migrated);
      migrated.rebuild(); assertCounter(migrated, 12);
    }
    try (var reopened = migratedCounter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
      assertCounter(reopened, 12);
      assertTrue(reopened.history(Counter.class, "a").getFirst().event().payload().isEmpty());
      assertInvalidatedSchemaCopiesRedacted(reopened);
    }
    try (var upgradedAgain = migratedCounter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state")))
        .schemaVersion(Counter.class, 3).upcaster(Counter.class, 2,
            value -> ((com.fasterxml.jackson.databind.node.ObjectNode) value).put("total", value.path("total").asInt() * 3))
        .schemaVersion(CounterView.class, 3).upcaster(CounterView.class, 2,
            value -> ((com.fasterxml.jackson.databind.node.ObjectNode) value).put("total", value.path("total").asInt() * 3)).build()) {
      assertCounter(upgradedAgain, 36);
      upgradedAgain.rebuild(); assertCounter(upgradedAgain, 36);
    }
    try (var reopened = migratedCounter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state")))
        .schemaVersion(Counter.class, 3).upcaster(Counter.class, 2,
            value -> ((com.fasterxml.jackson.databind.node.ObjectNode) value).put("total", value.path("total").asInt() * 3))
        .schemaVersion(CounterView.class, 3).upcaster(CounterView.class, 2,
            value -> ((com.fasterxml.jackson.databind.node.ObjectNode) value).put("total", value.path("total").asInt() * 3)).build()) {
      assertCounter(reopened, 36);
    }
  }

  private static void assertInvalidatedSchemaCopiesRedacted(Booster runtime) {
    var boundaries = runtime.eventStore().readAll(0, 1000).stream()
        .filter(stored -> stored.event().type().equals("__booster_schema_upgraded")).toList();
    assertEquals(2, boundaries.size(), "Both the entity and read-model copies must be covered");
    for (var stored : boundaries) {
      var event = stored.event();
      assertTrue(event.payload().isObject() && event.payload().isEmpty(), "Invalidated boundary payload must be redacted");
      String descriptorJson = event.metadata().get("__booster_schemaDescriptor");
      assertNotNull(descriptorJson);
      JsonNode descriptor = assertDoesNotThrow(() -> Json.MAPPER.readTree(descriptorJson));
      assertTrue(descriptor.path("sourcePosition").asLong() > 0);
      assertEquals(2, descriptor.path("schemaVersion").asInt());
      assertFalse(descriptor.has("state"), "The retained replay descriptor must not contain copied state");
      assertFalse(descriptorJson.contains("total"));
      var receipt = runtime.eventStore().findCommand(event.metadata().get("requestId")).orElseThrow();
      assertTrue(receipt.result().isNull());
      assertEquals(1, receipt.events().size());
      assertTrue(receipt.events().getFirst().event().payload().isEmpty(), "Receipt copies must be redacted too");
      assertEquals(descriptorJson, receipt.events().getFirst().event().metadata().get("__booster_schemaDescriptor"));
    }
  }

  @Test void schemaUpgradeAfterEarlierHistoricalDeletionMigratesTheRebuiltSurvivingState() {
    try (var runtime = counter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
      add(runtime, 10, request("removed"));
      var removed = runtime.history(Counter.class, "a").getFirst().event();
      add(runtime, 5, request("retained"));
      assertTrue(runtime.deleteEvent("Counter", "a", removed.timestamp(), request("remove")));
      runtime.rebuild(); assertCounter(runtime, 5);
    }
    try (var upgraded = migratedCounter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
      assertCounter(upgraded, 10);
      upgraded.rebuild(); assertCounter(upgraded, 10);
      add(upgraded, 2, request("after-upgrade")); assertCounter(upgraded, 12);
    }
    try (var reopened = migratedCounter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
      assertCounter(reopened, 12);
      reopened.rebuild(); assertCounter(reopened, 12);
    }
  }

  @Test void deletingUnrelatedStreamsAndTypesPreservesOtherEntitySchemaAnchors() {
    java.time.Instant unrelatedCounter, unrelatedOwner;
    try (var runtime = withOwner(counter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state")))).build()) {
      runtime.executeAction("owner", json("owner"), request("owner"), AccessPolicy.publicAccess(),
          (input, register) -> { register.emit(new OwnerCreated("owner")); return null; });
      runtime.executeAction("other-counter", json(new Added("b", 3)), request("counter-b"), AccessPolicy.publicAccess(),
          (input, register) -> { register.emit(Json.MAPPER.treeToValue(input, Added.class)); return null; });
      add(runtime, 10, request("counter-a"));
      unrelatedCounter = runtime.history(Counter.class, "b").getFirst().event().timestamp();
      unrelatedOwner = runtime.history(Owner.class, "owner").getFirst().event().timestamp();
    }
    try (var migrated = withOwner(migratedCounter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state")), 3)).build()) {
      assertEquals(20, migrated.entity(Counter.class, "a").total());
      assertEquals(30, migrated.getReadModel("CounterView", "a", request("model-before")).path("total").asInt());
      assertTrue(migrated.deleteEvent("Owner", "owner", unrelatedOwner, request("delete-owner")));
      assertEquals(20, migrated.entity(Counter.class, "a").total());
      assertEquals(30, migrated.getReadModel("CounterView", "a", request("model-after-unrelated")).path("total").asInt(),
          "An unrelated source type must preserve the read model's own migrated state");
      assertTrue(migrated.deleteEvent("Counter", "b", unrelatedCounter, request("delete-b")));
      assertCounter(migrated, 20);
      assertNull(migrated.entity(Counter.class, "b"));
      migrated.rebuild(); assertCounter(migrated, 20);
    }
    try (var reopened = withOwner(migratedCounter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state")), 3)).build()) {
      assertCounter(reopened, 20);
      assertNull(reopened.entity(Counter.class, "b"));
    }
  }

  private static Booster.Builder withOwner(Booster.Builder builder) {
    return builder.event(OwnerCreated.class, OwnerCreated::id, Owner.class, (event, previous) -> new Owner(event.id()))
        .eventAdministrationAccess(Owner.class, AccessPolicy.publicAccess());
  }

  @Test void schemaUpcasterCannotChangeSequenceIdentityWhileKeepingTheOldStorageKey() {
    try (var runtime = revision(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
      runtime.executeAction("write", json(new RevisionWritten("item", 1, 25)), request("write"), AccessPolicy.publicAccess(),
          (input, register) -> { register.emit(Json.MAPPER.treeToValue(input, RevisionWritten.class)); return null; });
    }
    try (var events = new JdbcEventStore(directory.resolve("journal")); var state = new JdbcStateStore(directory.resolve("state"))) {
      var builder = revision(events, state).schemaVersion(RevisionView.class, 2)
          .upcaster(RevisionView.class, 1, value -> ((com.fasterxml.jackson.databind.node.ObjectNode) value).put("revision", 2));
      assertThrows(BoosterException.class, builder::build, "Changing a sequence identity requires an explicit data migration/reprojection");
      var stored = state.scan("readModels:RevisionView", null, 10).getFirst().value();
      assertEquals(1, stored.path("state").path("revision").asInt());
      assertEquals(1, stored.path("sequenceValue").asInt());
    }
  }

  private static Booster.Builder revision(EventStore journal, StateStore state) {
    return Booster.builder(journal).stateStore(state).onError(error -> {})
        .event(RevisionWritten.class, RevisionWritten::id, RevisionEntity.class,
            (event, previous) -> new RevisionEntity(event.id(), event.revision(), event.value()))
        .readModel(RevisionView.class, RevisionEntity.class, AccessPolicy.publicAccess(),
            (entity, previous) -> new RevisionView(entity.id(), entity.revision(), entity.value()))
        .sequenceKey(RevisionView.class, "revision");
  }

  @Test void equivalentNumericSequenceValuesUseOneCompositeStorageIdentity() {
    try (var runtime = Booster.builder(new InMemoryEventStore()).onError(error -> {})
        .event(NumericWritten.class, NumericWritten::id, NumericSource.class,
            (event, previous) -> new NumericSource(event.id(), event.revision(), event.value()))
        .readModel(NumericView.class, AccessPolicy.publicAccess()).sequenceKey(NumericView.class, "revision")
        .projection(NumericView.class, NumericSource.class, Join.key(entity -> "joined"), context ->
            ProjectionResult.store(new NumericView(context.id(), context.entity().revision(),
                (context.previous() == null ? 0 : context.previous().total()) + context.entity().value())))
        .build()) {
      runtime.executeAction("first", json(new NumericWritten("first", 1, 2)), request("first"), AccessPolicy.publicAccess(),
          (input, register) -> { register.emit(Json.MAPPER.treeToValue(input, NumericWritten.class)); return null; });
      runtime.executeAction("second", json(new NumericWritten("second", 1.0, 3)), request("second"), AccessPolicy.publicAccess(),
          (input, register) -> { register.emit(Json.MAPPER.treeToValue(input, NumericWritten.class)); return null; });
      assertEquals(1, runtime.listReadModels("NumericView", QueryOptions.defaults(), request("all")).items().size());
      assertEquals(5, runtime.getReadModel("NumericView", "joined", 1, request("get")).path("total").asInt());
    }
  }

  @Test void deletionRevalidatesAfterConcurrentUnrelatedStreamIntroducesProjectionDependency() throws Exception {
    var reachedCommit = new CountDownLatch(1);
    var releaseCommit = new CountDownLatch(1);
    var first = new AtomicBoolean(true);
    var backing = new JdbcEventStore(directory.resolve("journal"));
    EventStore gated = new EventStore() {
      public Optional<CommandReceipt> findCommand(String id) { return backing.findCommand(id); }
      public CommitResult commit(String id, String fingerprint, Map<StreamId,Long> versions, List<PendingEvent> events, JsonNode result) {
        gate(id); return backing.commit(id, fingerprint, versions, events, result);
      }
      public CommitResult commitAtPosition(String id, String fingerprint, Map<StreamId,Long> versions, List<PendingEvent> events, JsonNode result, long position) {
        gate(id); return backing.commitAtPosition(id, fingerprint, versions, events, result, position);
      }
      private void gate(String id) {
        if (id.equals("delete-owner") && first.getAndSet(false)) {
          reachedCommit.countDown();
          try { assertTrue(releaseCommit.await(10, TimeUnit.SECONDS)); }
          catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        }
      }
      public List<StoredEvent> readStream(StreamId stream, long after) { return backing.readStream(stream, after); }
      public List<StoredEvent> readAll(long after, int limit) { return backing.readAll(after, limit); }
      public long lastPosition() { return backing.lastPosition(); }
      public boolean redactEventPayload(String id, java.time.Instant deletedAt) { return backing.redactEventPayload(id, deletedAt); }
      public void close() { backing.close(); }
    };
    try (var deleting = joined(gated, new JdbcStateStore(directory.resolve("state"))).build();
         var writing = joined(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build();
         var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      deleting.executeAction("owner", json("owner"), request("create-owner"), AccessPolicy.publicAccess(),
          (input, register) -> { register.emit(new OwnerCreated("owner")); return null; });
      var original = deleting.eventStore().readStream(new StreamId("Owner", "owner"), 0).getFirst().event();
      var deletion = executor.submit(() -> deleting.deleteEvent("Owner", "owner", original.timestamp(), request("delete-owner")));
      try {
        assertTrue(reachedCommit.await(5, TimeUnit.SECONDS));
        writing.executeAction("dependent", json("dependent"), request("create-dependent"), AccessPolicy.publicAccess(),
            (input, register) -> { register.emit(new DependentCreated("dependent")); return null; });
      } finally { releaseCommit.countDown(); }
      assertThrows(java.util.concurrent.ExecutionException.class, () -> deletion.get(5, TimeUnit.SECONDS));
      assertEquals(2, backing.lastPosition(), "Invalid deletion must not append a marker");
      assertFalse(backing.readStream(new StreamId("Owner", "owner"), 0).getFirst().event().payload().isEmpty());
      assertEquals(2, writing.getReadModel("SharedView", "shared", request("read")).path("references").asInt());
      writing.rebuild();
      assertEquals(2, writing.getReadModel("SharedView", "shared", request("replayed")).path("references").asInt());
    }
  }

  @Test void handlerRecoveryRefreshesDeletionIndexBeforeDecodingAnotherRuntimesRedactedEvent() {
    var fail = new AtomicBoolean(true);
    var handled = new java.util.concurrent.atomic.AtomicInteger();
    try (var stale = counter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state")))
        .eventHandler("retryable", Added.class, (event, register) -> {
          if (fail.get()) throw new IllegalStateException("Retry later");
          handled.incrementAndGet();
        }).build()) {
      add(stale, 1, request("add"));
      try (var deleting = counter(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build()) {
        var event = deleting.eventStore().readStream(new StreamId("Counter", "a"), 0).getFirst().event();
        assertTrue(deleting.deleteEvent("Counter", "a", event.timestamp(), request("delete")));
        fail.set(false);
        assertDoesNotThrow(stale::resumeHandlers, "Deleted events must be indexed before their redacted payloads are decoded");
        assertEquals(0, handled.get());
      }
    }
  }

  private static Booster.Builder joined(EventStore journal, StateStore state) {
    return Booster.builder(journal).stateStore(state).onError(error -> {})
        .event(OwnerCreated.class, OwnerCreated::id, Owner.class, (event, previous) -> new Owner(event.id()))
        .event(DependentCreated.class, DependentCreated::id, Dependent.class, (event, previous) -> new Dependent(event.id()))
        .readModel(SharedView.class, AccessPolicy.publicAccess())
        .projection(SharedView.class, Owner.class, Join.key(entity -> "shared"),
            context -> ProjectionResult.store(new SharedView(context.id(), 1)))
        .projection(SharedView.class, Dependent.class, Join.key(entity -> "shared"), context -> {
          if (context.previous() == null) throw new IllegalStateException("Owner projection is required");
          return ProjectionResult.store(new SharedView(context.id(), context.previous().references() + 1));
        })
        .eventAdministrationAccess(Owner.class, AccessPolicy.publicAccess());
  }
}
