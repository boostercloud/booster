package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.store.JdbcEventStore;
import io.boostercloud.booster.store.JdbcStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedCoreTest {
  record Team(String id, String title) {}
  record Member(String id, List<String> groups, String name) {}
  record TeamChanged(String id, String title) {}
  record MemberChanged(String id, List<String> groups, String name) {}
  record GroupView(String id, String title, Map<String,String> members) {}
  record RenamedTeam(String id, String title) {}
  record LegacyInput(String id, String title) {}
  record CountInput(String id, int count, List<Long> sizes) {}
  record Revision(String id, String aggregateId, int revision, String title) {}
  record RevisionChanged(String id, String aggregateId, int revision, String title) {}
  record RevisionView(String id, int revision, String title) {}
  private static RequestContext request(String id) { return new RequestContext(id, UserContext.anonymous(), Map.of()); }
  private static JsonNode json(Object value) { return Json.MAPPER.valueToTree(value); }
  private Booster.Builder domain(EventStore store, StateStore states) {
    return Booster.builder(store).stateStore(states).onError(error -> {})
        .event(TeamChanged.class, TeamChanged::id, Team.class, (event, before) -> new Team(event.id(), event.title()))
        .event(MemberChanged.class, MemberChanged::id, Member.class, (event, before) -> new Member(event.id(), event.groups(), event.name()))
        .entity(RenamedTeam.class)
        .readModel(GroupView.class, AccessPolicy.publicAccess())
        .projection(GroupView.class, Team.class, Join.key(Team::id), context -> ProjectionResult.store(
            new GroupView(context.id(), context.entity().title(), context.previous() == null ? Map.of() : context.previous().members())),
            context -> ProjectionResult.delete())
        .projection(GroupView.class, Member.class, Join.keys(Member::groups), context -> {
          var members = new TreeMap<String,String>();
          if (context.previous() != null) members.putAll(context.previous().members());
          members.put(context.entity().id(), context.entity().name());
          return ProjectionResult.store(new GroupView(context.id(), context.previous() == null ? null : context.previous().title(), members));
        }, context -> {
          if (context.previous() == null) return ProjectionResult.nothing();
          var members = new TreeMap<>(context.previous().members()); members.remove(context.previousEntity().id());
          return ProjectionResult.store(new GroupView(context.id(), context.previous().title(), members));
        });
  }
  private void emit(Booster runtime, String id, Object event) {
    runtime.executeAction("emit", json(event), request(id), AccessPolicy.publicAccess(), (input, register) -> { register.emit(event); return null; });
  }
  @Test void joinsMultipleSourcesFanOutUnprojectAndRebuildDeterministically() {
    var states = new InMemoryStateStore();
    try (var runtime = domain(new InMemoryEventStore(), states).build()) {
      emit(runtime, "team-a", new TeamChanged("a", "Alpha"));
      emit(runtime, "team-b", new TeamChanged("b", "Beta"));
      emit(runtime, "member-one", new MemberChanged("m", List.of("a", "b"), "One"));
      assertEquals("One", runtime.getReadModel("GroupView", "a", request("q")).path("members").path("m").asText());
      emit(runtime, "member-two", new MemberChanged("m", List.of("b"), "Two"));
      assertFalse(runtime.getReadModel("GroupView", "a", request("q")).path("members").has("m"));
      JsonNode expected = runtime.getReadModel("GroupView", "b", request("q"));
      assertEquals("Two", expected.path("members").path("m").asText());
      long version = states.get("readModels:GroupView", "b").orElseThrow().version();
      runtime.rebuild();
      assertEquals(expected, runtime.getReadModel("GroupView", "b", request("q")));
      assertEquals(version, states.get("readModels:GroupView", "b").orElseThrow().version());
      runtime.executeAction("delete", json("m"), request("delete"), AccessPolicy.publicAccess(), (input, register) -> { register.deleteEntity(Member.class, "m"); return null; });
      assertFalse(runtime.getReadModel("GroupView", "b", request("q")).path("members").has("m"));
    }
  }
  @Test void filterJoinsFindExistingModelsAndNothingPreservesVersion() {
    var states = new InMemoryStateStore();
    var builder = Booster.builder(new InMemoryEventStore()).stateStore(states)
        .event(TeamChanged.class, TeamChanged::id, Team.class, (event, before) -> new Team(event.id(), event.title()))
        .event(MemberChanged.class, MemberChanged::id, Member.class, (event, before) -> new Member(event.id(), event.groups(), event.name()))
        .readModel(GroupView.class, Team.class, AccessPolicy.publicAccess(), (entity, before) -> entity == null ? null : new GroupView(entity.id(), entity.title(), Map.of()))
        .projection(GroupView.class, Member.class, Join.filter(member -> json(Map.of("title", Map.of("eq", member.name())))), context ->
            ProjectionResult.store(new GroupView(context.id(), context.previous().title(), Map.of(context.entity().id(), context.entity().name()))),
            context -> ProjectionResult.nothing());
    try (var runtime = builder.build()) {
      emit(runtime, "a", new TeamChanged("a", "Alpha"));
      emit(runtime, "b", new TeamChanged("b", "Beta"));
      emit(runtime, "m", new MemberChanged("m", List.of(), "Alpha"));
      assertTrue(runtime.getReadModel("GroupView", "a", request("q")).path("members").has("m"));
      assertTrue(runtime.getReadModel("GroupView", "b", request("q")).path("members").isEmpty());
      long before = states.get("readModels:GroupView", "a").orElseThrow().version();
      emit(runtime, "change", new MemberChanged("m", List.of(), "Absent"));
      assertEquals(before, states.get("readModels:GroupView", "a").orElseThrow().version());
    }
  }
  @Test void explicitFlushSurvivesFailureAndRetryDoesNotAppendTheFlushedBatchAgain() {
    try (var runtime = domain(new InMemoryEventStore(), new InMemoryStateStore()).build()) {
      assertThrows(BoosterException.class, () -> runtime.executeAction("partial", json("a"), request("partial"), AccessPolicy.publicAccess(), (input, register) -> {
        register.emit(new TeamChanged("a", "Alpha")); register.flush();
        assertEquals(0, register.pendingEvents());
        throw new IllegalStateException("after durable boundary");
      }));
      assertEquals(1, runtime.position());
      assertEquals("Alpha", runtime.entity(Team.class, "a").title());
      runtime.executeAction("partial", json("a"), request("partial"), AccessPolicy.publicAccess(), (input, register) -> {
        register.emit(new TeamChanged("a", "Alpha")); register.flush(); return "done";
      });
      assertEquals(1, runtime.position());
      assertEquals("done", runtime.executeAction("partial", json("a"), request("partial"), AccessPolicy.publicAccess(),
          (input, register) -> { fail("durable receipt must skip handler"); return null; }).asText());
    }
  }
  @Test void migrationRenamesTypeAndIdWithoutDestroyingHistoryAndSurvivesRestart(@TempDir Path directory) {
    Path journal = directory.resolve("journal"), state = directory.resolve("state");
    try (var runtime = domain(new JdbcEventStore(journal), new JdbcStateStore(state)).build()) {
      emit(runtime, "initial", new TeamChanged("old", "Preserved"));
      runtime.executeAction("migrate", json("old"), request("migration"), AccessPolicy.publicAccess(), (input, register) -> {
        register.migrateEntity("Team", "old", new RenamedTeam("new", "Preserved")); return null;
      });
      assertNull(runtime.entity(Team.class, "old"));
      assertEquals("Preserved", runtime.entity(RenamedTeam.class, "new").title());
      assertEquals("TeamChanged", runtime.history(Team.class, "old").getFirst().event().type());
      assertTrue(runtime.getReadModel("GroupView", "old", request("q")).isNull());
    }
    try (var runtime = domain(new JdbcEventStore(journal), new JdbcStateStore(state)).build()) {
      assertNull(runtime.entity(Team.class, "old"));
      assertEquals("Preserved", runtime.entity(RenamedTeam.class, "new").title());
      assertEquals(3, runtime.position());
      assertEquals(2, runtime.history(Team.class, "old").size());
    }
  }
  @Test void schemaVersionsRejectGapsAndFutureAndMigrateCommandsWithoutMutatingInput() {
    var builder = domain(new InMemoryEventStore(), new InMemoryStateStore())
        .schemaVersion(LegacyInput.class, 2)
        .upcaster(LegacyInput.class, 1, node -> { var object = (com.fasterxml.jackson.databind.node.ObjectNode) node; object.set("title", object.remove("name")); return object; })
        .command(LegacyInput.class, Team.class, AccessPolicy.publicAccess(), (input, register) -> { register.emit(new TeamChanged(input.id(), input.title())); return register.entity(Team.class, input.id()); });
    try (var runtime = builder.build()) {
      JsonNode old = json(Map.of("id", "a", "name", "Alpha"));
      assertEquals("Alpha", runtime.executeCommand("LegacyInput", old, 1, request("old")).path("title").asText());
      assertTrue(old.has("name")); assertFalse(old.has("title"));
      assertThrows(BoosterException.class, () -> runtime.executeCommand("LegacyInput", old, 3, request("future")));
    }
    assertThrows(IllegalArgumentException.class, () -> domain(new InMemoryEventStore(), new InMemoryStateStore()).schemaVersion(Team.class, 3).build());
  }
  @Test void actionAuthorizationAndReceiptIdentityCannotCollideWithOtherActions() {
    var calls = new AtomicInteger();
    try (var runtime = domain(new InMemoryEventStore(), new InMemoryStateStore()).build()) {
      runtime.executeAction("first", json("input"), request("same"), AccessPolicy.publicAccess(), (input, register) -> calls.incrementAndGet());
      assertThrows(BoosterException.class, () -> runtime.executeAction("second", json("input"), request("same"), AccessPolicy.publicAccess(), (input, register) -> calls.incrementAndGet()));
      assertThrows(BoosterException.class, () -> runtime.executeAction("first", json("input"), request("same"), AccessPolicy.deny(), (input, register) -> calls.incrementAndGet()));
      assertEquals(1, calls.get());
    }
  }
  @Test void compositeKeysKeepComponentBoundariesAndRejectNulls() {
    assertNotEquals(Join.composite("a:b", "c"), Join.composite("a", "b:c"));
    assertNotEquals(Join.composite("1", 2), Join.composite(1, "2"));
    assertThrows(IllegalArgumentException.class, () -> Join.composite("a", null));
  }
  @Test void integralInputsPermitWholeFloatsAndRejectFractionalOrOverflowValues() {
    try (var runtime = domain(new InMemoryEventStore(), new InMemoryStateStore())
        .command(CountInput.class, CountInput.class, AccessPolicy.publicAccess(), (input, register) -> input).build()) {
      assertEquals(1, runtime.executeCommand("CountInput", json(Map.of("id", "a", "count", 1.0, "sizes", List.of(2.0))), request("whole")).path("count").asInt());
      assertThrows(BoosterException.class, () -> runtime.executeCommand("CountInput", json(Map.of("id", "a", "count", 1.5, "sizes", List.of(2))), request("fraction")));
      assertThrows(BoosterException.class, () -> runtime.executeCommand("CountInput", json(Map.of("id", "a", "count", Integer.MAX_VALUE + 1L, "sizes", List.of(2))), request("overflow")));
      assertThrows(BoosterException.class, () -> runtime.executeCommand("CountInput", json(Map.of("id", "a", "count", 1, "sizes", List.of(2.5))), request("nested")));
    }
  }
  @Test void sequencedModelsRetainLogicalIdAndDistinguishRevisionsInQueriesAndDeletionEvents() throws Exception {
    var states = new InMemoryStateStore();
    var builder = Booster.builder(new InMemoryEventStore()).stateStore(states)
        .event(RevisionChanged.class, RevisionChanged::id, Revision.class, (event, before) -> new Revision(event.id(), event.aggregateId(), event.revision(), event.title()))
        .readModel(RevisionView.class, AccessPolicy.publicAccess()).sequenceKey(RevisionView.class, "revision")
        .projection(RevisionView.class, Revision.class, Join.key(Revision::aggregateId), context -> ProjectionResult.store(
            new RevisionView(context.id(), context.entity().revision(), context.entity().title())), context -> ProjectionResult.delete());
    try (var runtime = builder.build()) {
      emit(runtime, "r1", new RevisionChanged("event-one", "logical", 1, "First"));
      emit(runtime, "r2", new RevisionChanged("event-two", "logical", 2, "Second"));
      assertThrows(BoosterException.class, () -> runtime.getReadModel("RevisionView", "logical", request("q")));
      assertEquals("First", runtime.getReadModel("RevisionView", "logical", 1.0, request("q")).path("title").asText());
      assertEquals("Second", runtime.getReadModel("RevisionView", "logical", 2, request("q")).path("title").asText());
      assertEquals(2, runtime.listReadModels("RevisionView", QueryOptions.defaults(), request("q")).items().size());
      var change = new java.util.concurrent.CompletableFuture<ReadModelChange>();
      runtime.subscribe("RevisionView", "logical", 2, request("sub")).subscribe(new java.util.concurrent.Flow.Subscriber<>() {
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
        public void onNext(ReadModelChange next) { change.complete(next); }
        public void onError(Throwable error) { change.completeExceptionally(error); }
        public void onComplete() {}
      });
      runtime.executeAction("delete-revision", json("event-two"), request("delete"), AccessPolicy.publicAccess(), (input, register) -> { register.deleteEntity(Revision.class, "event-two"); return null; });
      var deleted = change.get(5, java.util.concurrent.TimeUnit.SECONDS);
      assertTrue(deleted.value().isNull()); assertEquals("logical", deleted.id()); assertEquals(2, deleted.sequenceValue().asInt());
      assertTrue(runtime.getReadModel("RevisionView", "logical", 2, request("q")).isNull());
      var persisted = states.get("readModels:RevisionView", Join.composite("logical", json(2))).orElseThrow().value();
      assertEquals("logical", persisted.path("id").asText()); assertEquals(2, persisted.path("sequenceValue").asInt());
      runtime.rebuild();
      assertEquals("First", runtime.getReadModel("RevisionView", "logical", request("q")).path("title").asText());
    }
  }
  @Test void persistedSchemasUpcastWithoutRegressingPositionAndRejectFutureVersions() {
    var states = new InMemoryStateStore();
    var snapshot = Json.MAPPER.createObjectNode().put("position", 100).put("schemaVersion", 1);
    snapshot.set("state", json(Map.of("id", "a", "name", "Historic")));
    states.put("snapshots:Team", "a", 0, snapshot);
    var model = snapshot.deepCopy(); model.put("id", "a");
    ((com.fasterxml.jackson.databind.node.ObjectNode) model.get("state")).set("members", Json.MAPPER.createObjectNode());
    states.put("readModels:GroupView", "a", 0, model);
    var journal = new InMemoryEventStore();
    var stream = new StreamId("Team", "a");
    for (int i = 1; i <= 100; i++) journal.commit("old-" + i, "old-" + i, Map.of(stream, (long)i - 1),
        List.of(new PendingEvent("old-event-" + i, stream, "TeamChanged", 1, json(new TeamChanged("a", "Earlier")), java.time.Instant.now(), UserContext.anonymous(), Map.of())), Json.MAPPER.nullNode());
    var calls = new AtomicInteger();
    java.util.function.UnaryOperator<JsonNode> upgrade = value -> { calls.incrementAndGet(); var object = (com.fasterxml.jackson.databind.node.ObjectNode) value; object.set("title", object.remove("name")); return object; };
    try (var runtime = domain(journal, states)
        .schemaVersion(Team.class, 2).upcaster(Team.class, 1, upgrade)
        .schemaVersion(GroupView.class, 2).upcaster(GroupView.class, 1, upgrade).build()) {
      assertEquals(2, calls.get());
      assertEquals("Historic", states.get("snapshots:Team", "a").orElseThrow().value().path("state").path("title").asText());
      assertEquals("Historic", runtime.entity(Team.class, "a").title());
      assertEquals("Historic", runtime.getReadModel("GroupView", "a", request("read")).path("title").asText());
      runtime.rebuild();
      assertEquals("Historic", runtime.entity(Team.class, "a").title());
      assertTrue(states.get("snapshots:Team", "a").orElseThrow().value().path("position").asLong() >= 100);
      assertTrue(states.get("readModels:GroupView", "a").orElseThrow().value().path("position").asLong() >= 100);
      emit(runtime, "new-event", new TeamChanged("a", "Newer"));
      assertEquals("Newer", runtime.entity(Team.class, "a").title());
    }
    var future = new InMemoryStateStore(); future.put("snapshots:Team", "a", 0, snapshot.put("schemaVersion", 10));
    assertThrows(BoosterException.class, () -> domain(new InMemoryEventStore(), future).build());
  }
}
