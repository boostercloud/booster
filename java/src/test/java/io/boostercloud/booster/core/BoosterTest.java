package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class BoosterTest {
  record Account(String id, int balance) {}
  record AccountView(String id, int balance) {}
  record Open(String id, int balance) {}
  record Adjust(String id, int delta) {}
  record Remove(String id) {}
  record Opened(String id, int balance) {}
  record Adjusted(String id, int delta) {}
  record Removed(String id) {}
  record FindAccount(String id) {}

  private Booster.Builder base(EventStore store) {
    return Booster.builder(store).onError(error -> {})
        .event(Opened.class, Opened::id, Account.class, (event, previous) -> new Account(event.id(), event.balance()))
        .event(Adjusted.class, Adjusted::id, Account.class, (event, previous) -> {
          if (previous == null) throw new IllegalArgumentException("Account does not exist");
          return new Account(previous.id(), previous.balance() + event.delta());
        })
        .event(Removed.class, Removed::id, Account.class, (event, previous) -> null)
        .command(Open.class, Account.class, AccessPolicy.publicAccess(), (command, register) -> {
          if (register.entity(Account.class, command.id()) != null) throw new BoosterException(BoosterException.Code.CONFLICT, "Exists");
          register.emit(new Opened(command.id(), command.balance()));
          return register.entity(Account.class, command.id());
        })
        .command(Adjust.class, Account.class, AccessPolicy.publicAccess(), (command, register) -> {
          register.emit(new Adjusted(command.id(), command.delta()));
          return register.entity(Account.class, command.id());
        })
        .command(Remove.class, Void.class, AccessPolicy.publicAccess(), (command, register) -> { register.emit(new Removed(command.id())); return null; })
        .query(FindAccount.class, Account.class, AccessPolicy.publicAccess(), (query, context) -> context.entity(Account.class, query.id()))
        .readModel(AccountView.class, Account.class, AccessPolicy.publicAccess(), (account, previous) -> account == null ? null : new AccountView(account.id(), account.balance()));
  }
  private JsonNode json(Object object) { return Json.MAPPER.valueToTree(object); }
  private RequestContext request(String id) { return new RequestContext(id, UserContext.anonymous(), Map.of()); }

  @Test void commandsReplayEntitiesProjectQueriesDeleteAndDeduplicate() {
    try (var runtime = base(new InMemoryEventStore()).build()) {
      assertEquals(10, runtime.executeCommand("Open", json(new Open("a", 10)), request("one")).path("balance").asInt());
      assertEquals(10, runtime.executeCommand("Open", json(new Open("a", 10)), request("one")).path("balance").asInt());
      assertEquals(1, runtime.position());
      runtime.executeCommand("Adjust", json(new Adjust("a", 5)), request("two"));
      assertEquals(15, runtime.getReadModel("AccountView", "a", request("read")).path("balance").asInt());
      assertEquals(15, runtime.executeQuery("FindAccount", json(new FindAccount("a")), request("query")).path("balance").asInt());
      runtime.rebuild();
      assertEquals(15, runtime.getReadModel("AccountView", "a", request("replay")).path("balance").asInt());
      runtime.executeCommand("Remove", json(new Remove("a")), request("three"));
      assertTrue(runtime.getReadModel("AccountView", "a", request("deleted")).isNull());
      assertEquals(3, runtime.eventStore().readStream(new StreamId("Account", "a"), 0).size());
    }
  }

  @Test void failedHandlerDiscardsEveryBufferedEventAndDoesNotConsumeRequestId() {
    var fail = new AtomicBoolean(true);
    var store = new InMemoryEventStore();
    try (var runtime = Booster.builder(store).onError(error -> {})
        .event(Opened.class, Opened::id, Account.class, (event, previous) -> new Account(event.id(), event.balance()))
        .command(Open.class, Account.class, AccessPolicy.publicAccess(), (command, register) -> {
          register.emit(new Opened(command.id(), command.balance()));
          if (fail.get()) throw new IllegalStateException("Fail after emit");
          return register.entity(Account.class, command.id());
        }).build()) {
      assertThrows(BoosterException.class, () -> runtime.executeCommand("Open", json(new Open("a", 3)), request("retry")));
      assertEquals(0, store.lastPosition()); assertTrue(store.findCommand("retry").isEmpty());
      fail.set(false);
      runtime.executeCommand("Open", json(new Open("a", 3)), request("retry"));
      assertEquals(1, store.lastPosition());
    }
  }

  @Test void defaultAuthorizationDeniesBeforeHandlerAndHooks() {
    var called = new AtomicInteger();
    try (var runtime = Booster.builder(new InMemoryEventStore()).beforeCommand((name, register) -> called.incrementAndGet())
        .command(Open.class, (command, register) -> called.incrementAndGet()).build()) {
      var error = assertThrows(BoosterException.class, () -> runtime.executeCommand("Open", json(new Open("a", 1)), request("denied")));
      assertEquals(BoosterException.Code.FORBIDDEN, error.code()); assertEquals(0, called.get());
    }
  }

  @Test void readModelsAndQueriesRequireTheirOwnAuthorization() {
    var policy = AccessPolicy.roles("reader");
    try (var runtime = Booster.builder(new InMemoryEventStore()).entity(Account.class)
        .readModel(AccountView.class, Account.class, policy, (account, previous) -> null)
        .query(FindAccount.class, Account.class, policy, (query, context) -> context.entity(Account.class, query.id())).build()) {
      assertThrows(BoosterException.class, () -> runtime.getReadModel("AccountView", "a", request("read")));
      assertThrows(BoosterException.class, () -> runtime.listReadModels("AccountView", QueryOptions.defaults(), request("list")));
      assertThrows(BoosterException.class, () -> runtime.subscribe("AccountView", null, request("subscribe")));
      assertThrows(BoosterException.class, () -> runtime.executeQuery("FindAccount", json(new FindAccount("a")), request("query")));
      var authorized = new RequestContext("read", new UserContext("user", Set.of("reader"), Map.of()), Map.of());
      assertTrue(runtime.getReadModel("AccountView", "a", authorized).isNull());
    }
  }

  @Test void eventUpcastersReplayOldSchemaAndRejectFutureOrGappedVersions() {
    var store = new InMemoryEventStore();
    var stream = new StreamId("Account", "a");
    store.commit("old", "import", Map.of(stream, 0L), List.of(new PendingEvent("event", stream, "Opened", 1,
        json(Map.of("id", "a", "initial", 17)), Instant.now(), UserContext.anonymous(), Map.of())), NullNode.instance);
    try (var runtime = Booster.builder(store)
        .event(Opened.class, Opened::id, Account.class, (event, previous) -> new Account(event.id(), event.balance()), 2)
        .upcaster(Opened.class, 1, payload -> { var upgraded = Json.MAPPER.createObjectNode(); upgraded.set("id", payload.get("id")); upgraded.set("balance", payload.get("initial")); return upgraded; })
        .readModel(AccountView.class, Account.class, AccessPolicy.publicAccess(), (account, previous) -> new AccountView(account.id(), account.balance())).build()) {
      assertEquals(17, runtime.getReadModel("AccountView", "a", request("read")).path("balance").asInt());
    }
    assertThrows(IllegalArgumentException.class, () -> Booster.builder(new InMemoryEventStore())
        .event(Opened.class, Opened::id, Account.class, (event, previous) -> null, 3).build());
    var future = new InMemoryEventStore();
    future.commit("future", "import", Map.of(stream, 0L), List.of(new PendingEvent("event", stream, "Opened", 2,
        json(new Opened("a", 1)), Instant.now(), UserContext.anonymous(), Map.of())), NullNode.instance);
    assertThrows(BoosterException.class, () -> base(future).build());
    future.close();
  }

  @Test void snapshotReadAndReturnedResultsAreDefensiveCopies() {
    try (var runtime = base(new InMemoryEventStore()).build()) {
      var first = runtime.executeCommand("Open", json(new Open("a", 10)), request("one"));
      ((com.fasterxml.jackson.databind.node.ObjectNode) first).put("balance", 999);
      var projected = runtime.getReadModel("AccountView", "a", request("get"));
      ((com.fasterxml.jackson.databind.node.ObjectNode) projected).put("balance", 888);
      var event = runtime.eventStore().readAll(0, 1).getFirst().event();
      ((com.fasterxml.jackson.databind.node.ObjectNode) event.payload()).put("balance", 777);
      assertEquals(10, runtime.executeCommand("Open", json(new Open("a", 10)), request("one")).path("balance").asInt());
      runtime.rebuild();
      assertEquals(10, runtime.getReadModel("AccountView", "a", request("get2")).path("balance").asInt());
    }
  }

  @Test void subscriptionsDeliverCommittedChangesAndDeletionsWithBackpressure() throws Exception {
    try (var runtime = base(new InMemoryEventStore()).build()) {
      var values = new CopyOnWriteArrayList<ReadModelChange>();
      var latch = new CountDownLatch(2);
      runtime.subscribe("AccountView", "a", request("subscription")).subscribe(new Flow.Subscriber<>() {
        public void onSubscribe(Flow.Subscription subscription) { subscription.request(2); }
        public void onNext(ReadModelChange item) { values.add(item); latch.countDown(); }
        public void onError(Throwable error) { fail(error); }
        public void onComplete() {}
      });
      runtime.executeCommand("Open", json(new Open("b", 4)), request("other"));
      runtime.executeCommand("Open", json(new Open("a", 10)), request("one"));
      runtime.executeCommand("Remove", json(new Remove("a")), request("two"));
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertEquals(10, values.getFirst().value().path("balance").asInt());
      assertTrue(values.getLast().value().isNull()); assertTrue(values.getLast().position() > values.getFirst().position());
    }
  }

  @Test void intervalScheduleUsesNormalAuthorizationAndCommandPipeline() throws Exception {
    var completed = new CountDownLatch(1);
    try (var runtime = base(new InMemoryEventStore()).afterCommand((name, result) -> { if (name.equals("Adjust")) completed.countDown(); }).build()) {
      runtime.executeCommand("Open", json(new Open("a", 0)), request("create"));
      try (var schedule = runtime.schedule("credit", new Adjust("a", 1), Duration.ofMillis(20), UserContext.anonymous())) {
        assertTrue(completed.await(5, TimeUnit.SECONDS));
      }
      assertTrue(runtime.getReadModel("AccountView", "a", request("read")).path("balance").asInt() >= 1);
    }
  }

  @Test void commandRejectsUnknownFieldsEmptyEntityIdsAndUnknownTypes() {
    try (var runtime = base(new InMemoryEventStore()).build()) {
      assertEquals(BoosterException.Code.VALIDATION, assertThrows(BoosterException.class, () -> runtime.executeCommand("Open", json(Map.of("id", "a", "balance", 1, "typo", true)), request("bad"))).code());
      assertThrows(BoosterException.class, () -> runtime.executeCommand("Open", json(new Open("", 1)), request("empty")));
      assertEquals(BoosterException.Code.NOT_FOUND, assertThrows(BoosterException.class, () -> runtime.executeCommand("Missing", json(Map.of()), request("missing"))).code());
      assertEquals(0, runtime.position());
    }
  }

  @Test void namedHandlersPreserveCorrelationAndRunOnceAcrossRebuilds() {
    var invoked = new AtomicInteger();
    var correlation = new AtomicReference<String>();
    try (var runtime = base(new InMemoryEventStore()).eventHandler("give-bonus", Opened.class, (event, register) -> {
      invoked.incrementAndGet(); correlation.set(register.context().metadata().get("correlationId")); register.emit(new Adjusted(event.id(), 2));
    }).build()) {
      runtime.executeCommand("Open", json(new Open("a", 10)), request("root-command"));
      runtime.executeCommand("Open", json(new Open("a", 10)), request("root-command"));
      runtime.rebuild(); runtime.resumeHandlers();
      assertEquals(1, invoked.get()); assertEquals("root-command", correlation.get());
      assertEquals(12, runtime.getReadModel("AccountView", "a", request("get")).path("balance").asInt());
    }
  }

  @Test void emptyEventCommandsHaveDurableReceiptsAndCloseIsIdempotent() {
    var invoked = new AtomicInteger();
    var runtime = Booster.builder(new InMemoryEventStore()).command(Open.class, Integer.class, AccessPolicy.publicAccess(), (command, register) -> invoked.incrementAndGet()).build();
    assertEquals(1, runtime.executeCommand("Open", json(new Open("a", 1)), request("empty")).asInt());
    assertEquals(1, runtime.executeCommand("Open", json(new Open("a", 1)), request("empty")).asInt());
    assertEquals(0, runtime.position()); runtime.close(); runtime.close();
    assertThrows(IllegalStateException.class, runtime::position);
  }

  @Test void nestedClaimsAreImmutableSnapshotsIncludingJsonNulls() {
    Map<String,Object> nested = new HashMap<>(); nested.put("tenant", "first"); nested.put("optional", null);
    List<Object> tags = new ArrayList<>(); tags.add("reader"); tags.add(null);
    Map<String,Object> claims = new HashMap<>(); claims.put("profile", nested); claims.put("tags", tags);
    var user = new UserContext("subject", Set.of(), claims);
    nested.put("tenant", "changed"); tags.add("admin");
    assertEquals("first", ((Map<?,?>) user.claims().get("profile")).get("tenant"));
    assertEquals(2, ((List<?>) user.claims().get("tags")).size());
    assertThrows(UnsupportedOperationException.class, () -> ((Map<?,?>) user.claims().get("profile")).clear());
    assertThrows(UnsupportedOperationException.class, () -> ((List<?>) user.claims().get("tags")).clear());
    assertNull(((Map<?,?>) user.claims().get("profile")).get("optional"));
  }

  @Test void idempotencyDistinguishesRoleSetsWithAmbiguousDisplayStrings() {
    try (var runtime = base(new InMemoryEventStore()).build()) {
      var first = new RequestContext("request", new UserContext("subject", Set.of("a, b"), Map.of()), Map.of());
      var second = new RequestContext("request", new UserContext("subject", Set.of("a", "b"), Map.of()), Map.of());
      runtime.executeCommand("Open", json(new Open("a", 1)), first);
      assertEquals(BoosterException.Code.CONFLICT, assertThrows(BoosterException.class,
          () -> runtime.executeCommand("Open", json(new Open("a", 1)), second)).code());
    }
  }

  @Test void restartRecoversUnfinishedNamedEventHandlersWithoutAnotherCommand() {
    var store = new InMemoryEventStore();
    var invoked = new AtomicInteger();
    try (var original = base(store).eventHandler("bonus", Opened.class, (event, register) -> { throw new IllegalStateException("Temporary outage"); }).build()) {
      original.executeCommand("Open", json(new Open("a", 10)), request("first"));
      assertEquals(1, store.lastPosition());
      try (var restarted = base(store).eventHandler("bonus", Opened.class, (event, register) -> {
        invoked.incrementAndGet(); register.emit(new Adjusted(event.id(), 2));
      }).build()) {
        assertEquals(2, store.lastPosition());
        assertEquals(12, restarted.getReadModel("AccountView", "a", request("read")).path("balance").asInt());
        restarted.resumeHandlers(); assertEquals(1, invoked.get());
      }
    }
  }
}
