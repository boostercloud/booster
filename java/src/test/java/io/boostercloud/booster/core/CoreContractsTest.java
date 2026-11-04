package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.store.JdbcEventStore;
import io.boostercloud.booster.store.JdbcStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CoreContractsTest {
  record Wallet(String id, int balance) {}
  record WalletView(String id, int balance) {}
  record Opened(String id, int balance) {}
  record Withdrawn(String id, int amount) {}
  record Open(String id, int balance) {}
  record Query(String id) {}
  record Notice(String text, String partitionId) {}
  record EmptyNotice(String text) {}
  private static JsonNode json(Object value) { return Json.MAPPER.valueToTree(value); }
  private static RequestContext request(String id) { return new RequestContext(id, UserContext.anonymous(), Map.of()); }
  private Booster.Builder base(EventStore events, StateStore states) {
    return Booster.builder(events).stateStore(states).onError(error -> {})
        .event(Opened.class, Opened::id, Wallet.class, (event, previous) -> new Wallet(event.id(), event.balance()))
        .event(Withdrawn.class, Withdrawn::id, Wallet.class, (event, previous) -> {
          if (previous == null || previous.balance() < event.amount()) throw new IllegalArgumentException("Insufficient funds");
          return new Wallet(event.id(), previous.balance() - event.amount());
        })
        .readModel(WalletView.class, Wallet.class, AccessPolicy.publicAccess(), (entity, previous) -> entity == null ? null : new WalletView(entity.id(), entity.balance()))
        .command(Open.class, Wallet.class, AccessPolicy.publicAccess(), (input, register) -> { register.emit(new Opened(input.id(), input.balance())); return register.entity(Wallet.class, input.id()); })
        .query(Query.class, Wallet.class, AccessPolicy.publicAccess(), (input, context) -> context.entity(Wallet.class, input.id()));
  }
  private static void emit(Booster runtime, String id, Object event) {
    runtime.executeAction("emit", json(event), request(id), AccessPolicy.publicAccess(), (input, register) -> { register.emit(event); return null; });
  }
  @Test void notificationsNeedNoEntityAndGlobalAndTypedHandlersRecoverOnce(@TempDir Path directory) {
    var global = new AtomicInteger(); var typed = new AtomicInteger();
    Path path = directory.resolve("notifications");
    java.util.function.Function<EventStore,Booster> runtime = events -> Booster.builder(events)
        .notification(Notice.class).notification(EmptyNotice.class)
        .eventHandler("typed-notice", Notice.class, (notice, register) -> typed.incrementAndGet())
        .globalEventHandler("global-notice", (notice, register) -> global.incrementAndGet()).build();
    try (var booster = runtime.apply(new JdbcEventStore(path))) {
      emit(booster, "notice-a", new Notice("first", "tenant-a"));
      emit(booster, "notice-b", new EmptyNotice("second"));
      assertEquals(2, global.get()); assertEquals(1, typed.get());
      assertEquals("tenant-a", booster.eventStore().readAll(0, 2).getFirst().event().stream().entityId());
      assertEquals(new StreamId("defaultTopic", "default"), booster.eventStore().readAll(0, 2).getLast().event().stream());
      assertTrue(booster.stateStore().scan("snapshots:defaultTopic", null, 10).isEmpty());
    }
    try (var reopened = runtime.apply(new JdbcEventStore(path))) {
      reopened.resumeHandlers(); assertEquals(2, global.get()); assertEquals(1, typed.get());
    }
  }
  @Test void inputHooksRunInOrderWithRequestAwareAuthorizationAndDoNotMutateCallerData() {
    var builder = base(new InMemoryEventStore(), new InMemoryStateStore())
        .beforeCommand("Open", (input, context) -> { ((com.fasterxml.jackson.databind.node.ObjectNode)input).put("balance", input.path("balance").asInt() + 1); return input; })
        .beforeCommand("Open", (input, context) -> { ((com.fasterxml.jackson.databind.node.ObjectNode)input).put("balance", input.path("balance").asInt() * 2); return input; })
        .beforeQuery("Query", (input, context) -> json(Map.of("id", "a")))
        .beforeReadModel("WalletView", (input, context) -> {
          if (input.has("filter")) ((com.fasterxml.jackson.databind.node.ObjectNode)input).set("filter", json(Map.of("id", Map.of("eq", "a"))));
          return input;
        });
    try (var runtime = builder.build()) {
      JsonNode original = json(new Open("a", 4));
      assertEquals(10, runtime.executeCommand("Open", original, request("first")).path("balance").asInt());
      assertEquals(4, original.path("balance").asInt());
      emit(runtime, "b", new Opened("b", 20));
      assertEquals(10, runtime.executeQuery("Query", json(new Query("wrong")), request("q")).path("balance").asInt());
      assertEquals(List.of("a"), runtime.listReadModels("WalletView", QueryOptions.defaults(), request("list")).items().stream().map(value -> value.path("id").asText()).toList());
      assertEquals(10, runtime.executeCommand("Open", original, request("first")).path("balance").asInt());
    }
  }
  @Test void commandErrorSuppressionCommitsBufferedEventsAndReplacementPreservesTypedFailure() {
    record Fail(String id) {}
    var builder = base(new InMemoryEventStore(), new InMemoryStateStore())
        .command(Fail.class, Void.class, AccessPolicy.publicAccess(), (input, register) -> { register.emit(new Opened(input.id(), 7)); throw new IllegalStateException("handler"); })
        .onError(FrameworkError.Stage.COMMAND, failure -> null)
        .beforeQuery("Query", (input, context) -> { throw new IllegalStateException("query"); })
        .onError(FrameworkError.Stage.QUERY, failure -> new BoosterException(BoosterException.Code.VALIDATION, "replacement"));
    try (var runtime = builder.build()) {
      assertTrue(runtime.executeCommand("Fail", json(new Fail("a")), request("failed")).isNull());
      assertEquals(7, runtime.entity(Wallet.class, "a").balance());
      var failure = assertThrows(BoosterException.class, () -> runtime.executeQuery("Query", json(new Query("a")), request("q")));
      assertEquals(BoosterException.Code.VALIDATION, failure.code()); assertEquals("replacement", failure.getMessage());
      assertThrows(BoosterException.class, () -> runtime.executeSystemAction("system-failure", json("a"), request("__booster_system:failure"), AccessPolicy.publicAccess(), (input, register) -> {
        register.emit(new Opened("system", 1)); throw new IllegalStateException("must not be suppressed");
      }));
      assertNull(runtime.entity(Wallet.class, "system"));
    }
  }
  @Test void projectionErrorCanExplicitlySkipAndReducerSuppressionDoesNotInventState() {
    record BrokenView(String id) {}
    try (var runtime = base(new InMemoryEventStore(), new InMemoryStateStore())
        .readModel(BrokenView.class, Wallet.class, AccessPolicy.publicAccess(), (entity, before) -> { throw new IllegalStateException("projection"); })
        .onError(FrameworkError.Stage.PROJECTION, failure -> null)
        .onError(FrameworkError.Stage.REDUCER, failure -> null).build()) {
      emit(runtime, "open", new Opened("a", 3));
      assertTrue(runtime.getReadModel("BrokenView", "a", request("q")).isNull());
      assertThrows(BoosterException.class, () -> emit(runtime, "bad", new Withdrawn("a", 4)));
      assertEquals(1, runtime.position()); assertEquals(3, runtime.entity(Wallet.class, "a").balance());
    }
  }
  @Test void historyAccessIsDeniedByDefaultAndAuthorizedSearchHasStableDescendingTimeAndCursors() {
    try (var denied = base(new InMemoryEventStore(), new InMemoryStateStore()).build()) {
      assertThrows(BoosterException.class, () -> denied.searchEvents("Wallet", null, null, null, null, 10, request("read")));
    }
    try (var runtime = base(new InMemoryEventStore(), new InMemoryStateStore()).eventHistoryAccess(Wallet.class, AccessPolicy.publicAccess()).build()) {
      emit(runtime, "a", new Opened("a", 1)); emit(runtime, "b", new Opened("b", 2)); emit(runtime, "c", new Opened("c", 3));
      assertEquals(List.of("c", "b"), runtime.searchEvents(null, "Opened", null, null, null, 2, request("read")).stream().map(event -> event.event().stream().entityId()).toList());
      var event = runtime.searchEvents("Wallet", null, "b", null, null, 10, request("read")).getFirst();
      assertEquals(1, runtime.searchEvents("Wallet", null, null, event.event().timestamp(), event.event().timestamp(), 10, request("read")).size());
      var page = runtime.entitiesIDs("Wallet", 2, null, request("ids")); assertEquals(List.of("a", "b"), page.items());
      assertEquals(List.of("c"), runtime.entitiesIDs("Wallet", 2, page.cursor(), request("ids")).items());
      assertThrows(BoosterException.class, () -> runtime.searchEvents("Wallet", "Opened", null, null, null, 2, request("bad")));
    }
  }
  @Test void deletingAnEventClearsPayloadAndReceiptCopyAndReprojectsAcrossRuntimesAndRestart(@TempDir Path directory) {
    Path path = directory.resolve("delete-journal"); Path state = directory.resolve("delete-state");
    java.util.function.Supplier<Booster> open = () -> base(new JdbcEventStore(path), new JdbcStateStore(state))
        .eventHistoryAccess(Wallet.class, AccessPolicy.publicAccess()).eventAdministrationAccess(Wallet.class, AccessPolicy.publicAccess()).build();
    try (var first = open.get(); var second = open.get()) {
      emit(first, "initial", new Opened("a", 100)); emit(first, "withdraw", new Withdrawn("a", 80));
      assertEquals(20, second.entity(Wallet.class, "a").balance());
      var event = first.eventStore().findCommand("withdraw").orElseThrow().events().getFirst();
      assertTrue(first.deleteEvent("Wallet", "a", event.event().timestamp(), request("delete")));
      assertEquals(100, first.entity(Wallet.class, "a").balance()); assertEquals(100, second.entity(Wallet.class, "a").balance());
      assertTrue(first.eventStore().readStream(new StreamId("Wallet", "a"), 0).get(1).event().payload().isEmpty());
      assertTrue(first.eventStore().findCommand("withdraw").orElseThrow().events().getFirst().event().payload().isEmpty());
      assertEquals(1, first.searchEvents("Wallet", null, "a", null, null, 10, request("read")).size());
      assertTrue(first.deleteEvent("Wallet", "a", event.event().timestamp(), request("delete")));
      assertFalse(first.deleteEvent("Wallet", "a", event.event().timestamp(), request("delete-again")));
      assertEquals(3, first.position());
    }
    try (var runtime = open.get()) { assertEquals(100, runtime.entity(Wallet.class, "a").balance()); assertEquals(100, runtime.getReadModel("WalletView", "a", request("q")).path("balance").asInt()); }
  }
  @Test void deletionRejectsAReplayThatWouldBreakDependentReducers() {
    try (var runtime = base(new InMemoryEventStore(), new InMemoryStateStore()).eventAdministrationAccess(Wallet.class, AccessPolicy.publicAccess()).build()) {
      emit(runtime, "open", new Opened("a", 100)); emit(runtime, "withdraw", new Withdrawn("a", 10));
      var opened = runtime.eventStore().readAll(0, 1).getFirst();
      assertThrows(BoosterException.class, () -> runtime.deleteEvent("Wallet", "a", opened.event().timestamp(), request("delete")));
      assertEquals(2, runtime.position()); assertEquals(100, runtime.eventStore().readAll(0, 1).getFirst().event().payload().path("balance").asInt());
    }
  }
  @Test void partialFlushRetryReplaysOriginalReadsAndReducersInsteadOfChargingTwice() {
    try (var runtime = base(new InMemoryEventStore(), new InMemoryStateStore()).build()) {
      emit(runtime, "open", new Opened("a", 100));
      assertThrows(BoosterException.class, () -> runtime.executeAction("withdraw", json("a"), request("partial"), AccessPolicy.publicAccess(), (input, register) -> {
        assertEquals(100, register.entity(Wallet.class, "a").balance());
        register.emit(new Withdrawn("a", 80)); register.flush(); throw new IllegalStateException("interrupted");
      }));
      assertEquals(20, runtime.entity(Wallet.class, "a").balance());
      JsonNode result = runtime.executeAction("withdraw", json("a"), request("partial"), AccessPolicy.publicAccess(), (input, register) -> {
        assertEquals(100, register.entity(Wallet.class, "a").balance());
        register.emit(new Withdrawn("a", 80)); register.flush(); return register.entity(Wallet.class, "a");
      });
      assertEquals(20, result.path("balance").asInt()); assertEquals(2, runtime.position());
    }
  }
  @Test void stableRequestHeadersScopeIdempotencyWithoutPersistingRawRequestSecrets() {
    record TenantInput(String id) {}
    var context = new RequestContext("scope", UserContext.anonymous(), Map.of(), new RequestData(Map.of("X-Tenant", List.of("one"), "Authorization", List.of("Bearer secret")), json(Map.of("secret", "hidden")), new Object()));
    try (var runtime = base(new InMemoryEventStore(), new InMemoryStateStore())
        .command(TenantInput.class, String.class, AccessPolicy.input((request, input) -> request.request().headers().containsKey("x-tenant")), (input, register) -> {
          register.emit(new Opened(input.id(), 1)); register.responseHeader("X-Test", "yes"); return register.context().request().headers().get("x-tenant").getFirst();
        }).build()) {
      assertEquals("one", runtime.executeCommand("TenantInput", json(new TenantInput("a")), context).asText());
      assertEquals("yes", context.response().snapshot().get("X-Test"));
      var other = new RequestContext("scope", UserContext.anonymous(), Map.of(), new RequestData(Map.of("X-Tenant", List.of("two")), context.request().body(), null));
      assertThrows(BoosterException.class, () -> runtime.executeCommand("TenantInput", json(new TenantInput("a")), other));
      String stored = json(runtime.eventStore().findCommand("scope").orElseThrow()).toString();
      assertFalse(stored.contains("Bearer secret")); assertFalse(stored.contains("hidden"));
      assertFalse(json(context).has("request")); assertFalse(json(context).has("response"));
    }
  }
}
