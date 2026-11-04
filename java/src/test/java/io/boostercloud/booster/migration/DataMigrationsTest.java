package io.boostercloud.booster.migration;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.store.JdbcEventStore;
import io.boostercloud.booster.store.JdbcStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DataMigrationsTest {
  @TempDir Path directory;
  record Account(String id, int balance) {}
  record RenamedAccount(String id, int balance) {}
  record AccountView(String id, int balance) {}
  record RenamedView(String id, int balance) {}
  record Credited(String id, int amount) {}
  private static final UserContext IDENTITY = new UserContext("migration-worker", Set.of("operator"), Map.of());

  private Booster.Builder application(EventStore journal, StateStore state) {
    return DataMigrations.install(Booster.builder(journal).stateStore(state).onError(error -> {}))
        .event(Credited.class, Credited::id, Account.class,
            (event, old) -> new Account(event.id(), (old == null ? 0 : old.balance()) + event.amount()))
        .entity(RenamedAccount.class)
        .readModel(AccountView.class, Account.class, AccessPolicy.publicAccess(),
            (account, old) -> account == null ? null : new AccountView(account.id(), account.balance()))
        .readModel(RenamedView.class, RenamedAccount.class, AccessPolicy.publicAccess(),
            (account, old) -> account == null ? null : new RenamedView(account.id(), account.balance()));
  }
  private Booster memory() { return application(new InMemoryEventStore(), new InMemoryStateStore()).build(); }
  private Booster persistent() {
    return application(new JdbcEventStore(directory.resolve("journal")), new JdbcStateStore(directory.resolve("state"))).build();
  }
  private DataMigrations engine(Booster booster, DataMigration... definitions) {
    return new DataMigrations(booster, List.of(definitions), IDENTITY);
  }
  private static JsonNode json(Object value) { return Json.MAPPER.valueToTree(value); }
  private void credit(Booster booster, String id, int amount) {
    booster.executeAction("seed", json(new Credited(id, amount)), RequestContext.anonymous(), AccessPolicy.publicAccess(),
        (ignored, register) -> { register.emit(new Credited(id, amount)); return null; });
  }

  @Test void completionRequiresExplicitEventAndOrderingWaitsForIt() {
    var calls = new ArrayList<String>();
    var first = new DataMigration("first", 1, context -> calls.add("first"));
    var second = new DataMigration("second", 2, context -> { calls.add("second"); context.finish(); });
    try (var booster = memory()) {
      var migrations = engine(booster, second, first);
      assertTrue(migrations.run());
      assertEquals(List.of("first"), calls);
      assertEquals(DataMigrationStatus.RUNNING, migrations.status("first").orElseThrow().lifecycle().status());
      assertTrue(migrations.status("second").isEmpty());
      assertTrue(migrations.run());
      assertEquals(List.of("first"), calls);
      migrations.finish("first");
      long finishedPosition = booster.position();
      migrations.finish("first");
      assertEquals(finishedPosition, booster.position(), "Repeated completion must not emit another event");
      assertTrue(migrations.run());
      assertEquals(List.of("first", "second"), calls);
      assertFalse(migrations.run());
      assertEquals(2, migrations.statuses().size());
    }
  }

  @Test void publicActionsCannotPreclaimMigrationLifecycleReceipts() throws Exception {
    String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
        .digest("protected".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    String internalId = "__booster_migration:" + digest + ":start:1";
    try (var booster = memory()) {
      var rejected = assertThrows(BoosterException.class, () -> booster.executeAction("public-operation", json(Map.of()),
          new RequestContext(internalId, UserContext.anonymous(), Map.of()), AccessPolicy.publicAccess(), (input, register) -> null));
      assertEquals(BoosterException.Code.VALIDATION, rejected.code());
      assertTrue(booster.eventStore().findCommand(internalId).isEmpty());
      var migrations = engine(booster, new DataMigration("protected", 1, context -> context.finish()));
      assertTrue(migrations.run());
      assertEquals(DataMigrationStatus.FINISHED, migrations.status("protected").orElseThrow().lifecycle().status());
      assertTrue(booster.eventStore().findCommand(internalId).isPresent());
    }
  }

  @Test void explicitFinishedEventThroughRegisterUsesBaselineContract() {
    try (var booster = memory()) {
      var migrations = engine(booster, new DataMigration("manual", 1, context ->
          context.register().emit(new BoosterDataMigrationFinished("manual", Instant.parse("2026-11-04T12:00:00Z")))));
      assertTrue(migrations.run());
      assertEquals(DataMigrationStatus.FINISHED, migrations.status("manual").orElseThrow().lifecycle().status());
      assertFalse(migrations.run());
      assertEquals(List.of("BoosterDataMigrationStarted", "BoosterDataMigrationFinished"),
          booster.history(BoosterDataMigrationEntity.class, "manual").stream().map(event -> event.event().type()).toList());
    }
  }

  @Test void failedMigrationResumesAfterDatabaseReopenWithoutRepeatingCommittedStep() {
    var failOnce = new AtomicBoolean(true);
    var stepCalls = new AtomicInteger();
    var definition = new DataMigration("credit-accounts", 1, json(Map.of("amount", 25)), context -> {
      context.step("credit:a", json(new Credited("a", context.parameters().path("amount").asInt())), (input, register) -> {
        stepCalls.incrementAndGet(); register.emit(Json.MAPPER.treeToValue(input, Credited.class)); return Map.of("done", true);
      });
      if (failOnce.getAndSet(false)) throw new IllegalStateException("Interrupted after committing the step");
      context.checkpoint(json(Map.of("lastAccount", "a")));
      context.finish();
    });
    try (var booster = persistent()) {
      var migrations = engine(booster, definition);
      assertThrows(BoosterException.class, migrations::run);
      assertEquals(DataMigrationStatus.FAILED, migrations.status("credit-accounts").orElseThrow().lifecycle().status());
      assertEquals("credit:a", migrations.status("credit-accounts").orElseThrow().lastCompletedStep());
      assertEquals(25, booster.entity(Account.class, "a").balance());
      assertEquals(1, booster.history(Account.class, "a").size());
    }
    try (var booster = persistent()) {
      var migrations = engine(booster, definition);
      assertTrue(migrations.run(), "Ordinary run reports failed work but does not silently retry it");
      assertEquals(1, stepCalls.get());
      assertTrue(migrations.resume());
      var status = migrations.status("credit-accounts").orElseThrow();
      assertEquals(DataMigrationStatus.FINISHED, status.lifecycle().status());
      assertEquals(2, status.lifecycle().attempts());
      assertEquals("a", status.checkpoint().path("lastAccount").asText());
      assertEquals(1, stepCalls.get());
      assertEquals(25, booster.entity(Account.class, "a").balance());
      assertEquals(1, booster.history(Account.class, "a").size());
      assertFalse(migrations.resume());
    }
  }

  @Test void resumedStepReplaysFlushedDomainPrefixWithTheCurrentMigrationFence() {
    var failOnce = new AtomicBoolean(true);
    try (var booster = memory()) {
      credit(booster, "account", 100);
      var migrations = engine(booster, new DataMigration("partial-withdrawal", 1, context -> {
        context.step("withdraw-80", json(Map.of("amount", 80)), (input, register) -> {
          Account account = register.entity(Account.class, "account");
          if (account.balance() < input.path("amount").asInt()) throw new IllegalStateException("Insufficient funds");
          register.emit(new Credited("account", -input.path("amount").asInt()));
          register.flush();
          if (failOnce.getAndSet(false)) throw new IllegalStateException("Interrupted after partial step flush");
          return null;
        });
        context.finish();
      }));
      assertThrows(BoosterException.class, migrations::run);
      assertEquals(20, booster.entity(Account.class, "account").balance());
      assertTrue(migrations.resume());
      assertEquals(DataMigrationStatus.FINISHED, migrations.status("partial-withdrawal").orElseThrow().lifecycle().status());
      assertEquals(20, booster.entity(Account.class, "account").balance());
      assertEquals(2, booster.history(Account.class, "account").size(), "A replayed flush must not withdraw twice");
    }
  }

  @Test void checkpointSurvivesRestartAndParametersCannotBeMutatedThroughContext() {
    var first = new AtomicBoolean(true);
    var definition = new DataMigration("checkpoint", 1, json(Map.of("mode", "stable")), context -> {
      ((com.fasterxml.jackson.databind.node.ObjectNode) context.parameters()).put("mode", "changed");
      assertEquals("stable", context.parameters().path("mode").asText());
      if (first.getAndSet(false)) { context.checkpoint(json(Map.of("cursor", 100))); return; }
      assertEquals(100, context.checkpoint().path("cursor").asInt()); context.finish();
    });
    try (var booster = persistent()) { assertTrue(engine(booster, definition).run()); }
    try (var booster = persistent()) {
      var migrations = engine(booster, definition);
      assertEquals(DataMigrationStatus.RUNNING, migrations.status("checkpoint").orElseThrow().lifecycle().status());
      assertTrue(migrations.resume());
      assertEquals(DataMigrationStatus.FINISHED, migrations.status("checkpoint").orElseThrow().lifecycle().status());
    }
  }

  @Test void reusedStepWithDifferentInputFailsInsteadOfOverwritingItsReceipt() {
    try (var booster = memory()) {
      var migrations = engine(booster, new DataMigration("conflicting-step", 1, context -> {
        context.step("account", json(new Credited("a", 1)), (input, register) -> {
          register.emit(Json.MAPPER.treeToValue(input, Credited.class)); return null;
        });
        context.step("account", json(new Credited("a", 2)), (input, register) -> {
          register.emit(Json.MAPPER.treeToValue(input, Credited.class)); return null;
        });
        context.finish();
      }));
      assertEquals(BoosterException.Code.CONFLICT, assertThrows(BoosterException.class, migrations::run).code());
      assertEquals(1, booster.entity(Account.class, "a").balance());
      assertEquals(1, booster.history(Account.class, "a").size());
    }
  }

  @Test void renameIdAndTouchKeepHistoricalEventsAndRebuildTheReplacementProjection() {
    var once = new AtomicBoolean(true);
    var migration = new DataMigration("rename-account", 1, context -> {
      context.migrateEntity("rename:a", "Account", "a", new RenamedAccount("renamed-a", 18));
      context.touch("touch:renamed-a", RenamedAccount.class, "renamed-a");
      if (once.getAndSet(false)) throw new IllegalStateException("After rename and touch");
      context.finish();
    });
    try (var booster = persistent()) {
      credit(booster, "a", 18);
      var migrations = engine(booster, migration);
      assertThrows(BoosterException.class, migrations::run);
      assertNull(booster.entity(Account.class, "a"));
      assertEquals(18, booster.entity(RenamedAccount.class, "renamed-a").balance());
      assertTrue(booster.getReadModel("AccountView", "a", RequestContext.anonymous()).isNull());
      assertEquals(18, booster.getReadModel("RenamedView", "renamed-a", RequestContext.anonymous()).path("balance").asInt());
      assertEquals("Credited", booster.history(Account.class, "a").getFirst().event().type());
    }
    try (var booster = persistent()) {
      var migrations = engine(booster, migration);
      long oldSize = booster.history(Account.class, "a").size();
      long replacementSize = booster.history(RenamedAccount.class, "renamed-a").size();
      assertTrue(migrations.resume());
      booster.rebuild();
      assertNull(booster.entity(Account.class, "a"));
      assertEquals(18, booster.entity(RenamedAccount.class, "renamed-a").balance());
      assertEquals(oldSize, booster.history(Account.class, "a").size());
      assertEquals(replacementSize, booster.history(RenamedAccount.class, "renamed-a").size());
    }
  }

  @Test void independentWorkersCannotExecuteTheSameMigrationConcurrently() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    var definition = new DataMigration("slow", 1, context -> {
      calls.incrementAndGet(); entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); context.finish();
    });
    try (var booster = persistent(); var other = persistent(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = engine(booster, definition);
      var second = engine(other, definition);
      var running = executor.submit(first::run);
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertEquals(BoosterException.Code.CONFLICT, assertThrows(BoosterException.class, second::resume).code());
        assertEquals(1, calls.get());
      } finally { release.countDown(); }
      assertTrue(running.get(5, TimeUnit.SECONDS));
      assertFalse(second.run());
      assertEquals(1, calls.get());
    }
  }

  @Test void expiredWorkerCannotCommitABufferedFinishAfterAnotherWorkerTakesOver() throws Exception {
    var clock = new MutableClock(Instant.parse("2026-11-04T00:00:00Z"));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var attempts = new AtomicInteger();
    var definition = new DataMigration("expired", 1, context -> {
      if (attempts.incrementAndGet() == 1) {
        entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS));
      }
      context.finish();
    });
    try (var booster = persistent(); var other = persistent(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = new DataMigrations(booster, List.of(definition), IDENTITY, clock, Duration.ofMinutes(1));
      var second = new DataMigrations(other, List.of(definition), IDENTITY, clock, Duration.ofMinutes(1));
      var running = executor.submit(first::run);
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        clock.advance(Duration.ofMinutes(2));
        assertTrue(second.resume());
      } finally { release.countDown(); }
      assertThrows(java.util.concurrent.ExecutionException.class, () -> running.get(5, TimeUnit.SECONDS));
      assertEquals(DataMigrationStatus.FINISHED, second.status("expired").orElseThrow().lifecycle().status());
      long completed = other.history(BoosterDataMigrationEntity.class, "expired").stream()
          .filter(event -> event.event().type().equals("BoosterDataMigrationFinished")).count();
      assertEquals(1, completed);
    }
  }

  @Test void lifecycleVersionFencesBufferedEffectsWhenLeaseExpiresImmediatelyBeforeJournalCommit() throws Exception {
    var clock = new MutableClock(Instant.parse("2026-11-04T00:00:00Z"));
    var beforeCommit = new CountDownLatch(1);
    var releaseCommit = new CountDownLatch(1);
    var firstCommit = new AtomicBoolean(true);
    var calls = new AtomicInteger();
    var definition = new DataMigration("fenced", 1, context -> {
      int attempt = calls.incrementAndGet();
      context.register().emit(new Credited("flushed-" + attempt, 1));
      context.register().flush();
      context.register().emit(new Credited("attempt-" + attempt, 1));
      if (attempt > 1) context.finish();
    });
    var backing = new JdbcEventStore(directory.resolve("journal"));
    EventStore gated = new EventStore() {
      public java.util.Optional<CommandReceipt> findCommand(String id) { return backing.findCommand(id); }
      public CommitResult commit(String id, String fingerprint, Map<StreamId,Long> expected, List<PendingEvent> events, JsonNode result) {
        gate(id);
        return backing.commit(id, fingerprint, expected, events, result);
      }
      public CommitResult commitAtPosition(String id, String fingerprint, Map<StreamId,Long> expected, List<PendingEvent> events, JsonNode result, long position) {
        gate(id);
        return backing.commitAtPosition(id, fingerprint, expected, events, result, position);
      }
      private void gate(String id) {
        if (id.endsWith(":run:1") && firstCommit.getAndSet(false)) {
          beforeCommit.countDown();
          try { assertTrue(releaseCommit.await(10, TimeUnit.SECONDS)); }
          catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        }
      }
      public List<StoredEvent> readStream(StreamId stream, long after) { return backing.readStream(stream, after); }
      public List<StoredEvent> readAll(long after, int limit) { return backing.readAll(after, limit); }
      public long lastPosition() { return backing.lastPosition(); }
      public void close() { backing.close(); }
    };
    try (var booster = application(gated, new JdbcStateStore(directory.resolve("state"))).build();
         var other = persistent(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = new DataMigrations(booster, List.of(definition), IDENTITY, clock, Duration.ofMinutes(1));
      var second = new DataMigrations(other, List.of(definition), IDENTITY, clock, Duration.ofMinutes(1));
      var running = executor.submit(first::run);
      try {
        assertTrue(beforeCommit.await(5, TimeUnit.SECONDS));
        clock.advance(Duration.ofMinutes(2));
        assertTrue(second.resume());
      } finally { releaseCommit.countDown(); }
      assertThrows(java.util.concurrent.ExecutionException.class, () -> running.get(5, TimeUnit.SECONDS));
      assertNull(other.entity(Account.class, "attempt-1"));
      assertEquals(1, other.entity(Account.class, "flushed-1").balance(), "Previously flushed effects remain durable");
      assertEquals(1, other.entity(Account.class, "attempt-2").balance());
      assertEquals(DataMigrationStatus.FINISHED, second.status("fenced").orElseThrow().lifecycle().status());
    }
  }

  @Test void lifecycleDurationUsesEventTimestampsAndIncludesResumption() {
    var clock = new MutableClock(Instant.parse("2026-11-04T00:00:00Z"));
    try (var booster = memory()) {
      var migrations = new DataMigrations(booster, List.of(new DataMigration("timed", 1, context -> {
        clock.advance(Duration.ofMillis(1234)); context.finish();
      })), IDENTITY, clock, Duration.ofMinutes(1));
      assertTrue(migrations.run());
      assertEquals(1234L, migrations.status("timed").orElseThrow().lifecycle().duration().elapsedMilliseconds());
      booster.rebuild();
      assertEquals(1234L, migrations.status("timed").orElseThrow().lifecycle().duration().elapsedMilliseconds());
    }
  }

  @Test void durableCatalogRejectsChangedOrRemovedJobsAndAllowsAppendingNewJobs() {
    var first = new DataMigration("first", 1, json(Map.of("v", 1)), context -> context.finish());
    try (var booster = persistent()) { assertTrue(engine(booster, first).run()); }
    try (var booster = persistent()) {
      assertThrows(BoosterException.class, () -> engine(booster));
      assertThrows(BoosterException.class, () -> engine(booster,
          new DataMigration("first", 1, json(Map.of("v", 2)), context -> context.finish())));
      assertThrows(BoosterException.class, () -> engine(booster, new DataMigration("before", 0, context -> context.finish()), first));
      var migrations = engine(booster, first, new DataMigration("second", 2, context -> context.finish()));
      assertTrue(migrations.run());
      assertEquals(2, migrations.statuses().size());
    }
  }

  @Test void finishBeforeStartAndAfterFailureAreRejectedAndBufferedFinishRollsBackOnFailure() {
    try (var booster = memory()) {
      var migrations = engine(booster, new DataMigration("invalid", 1, context -> {
        context.finish(); throw new IllegalStateException("after buffered completion");
      }));
      assertThrows(IllegalStateException.class, () -> migrations.finish("invalid"));
      assertThrows(BoosterException.class, migrations::run);
      assertEquals(DataMigrationStatus.FAILED, migrations.status("invalid").orElseThrow().lifecycle().status());
      assertThrows(IllegalStateException.class, () -> migrations.finish("invalid"));
      assertTrue(booster.history(BoosterDataMigrationEntity.class, "invalid").stream()
          .noneMatch(event -> event.event().type().equals("BoosterDataMigrationFinished")));
    }
  }

  @Test void userCommandErrorSuppressionCannotTurnFailedSystemMigrationIntoSuccess() {
    var commandHooks = new AtomicInteger();
    try (var booster = application(new InMemoryEventStore(), new InMemoryStateStore())
        .beforeCommand((name, register) -> commandHooks.incrementAndGet())
        .onError(FrameworkError.Stage.COMMAND, error -> null).build()) {
      var migrations = engine(booster, new DataMigration("system-failure", 1, context -> {
        context.finish(); throw new IllegalStateException("Do not suppress failed migration");
      }));
      assertThrows(BoosterException.class, migrations::run);
      assertEquals(DataMigrationStatus.FAILED, migrations.status("system-failure").orElseThrow().lifecycle().status());
      assertEquals(0, commandHooks.get(), "Migration lifecycle is not a public command dispatch");
      assertTrue(booster.history(BoosterDataMigrationEntity.class, "system-failure").stream()
          .noneMatch(event -> event.event().type().equals("BoosterDataMigrationFinished")));
    }
  }

  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> instant;
    MutableClock(Instant instant) { this.instant = new AtomicReference<>(instant); }
    void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant.get(); }
  }
}
