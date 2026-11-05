package io.boostercloud.booster.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.store.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistentSchedulerTest {
  @TempDir Path temporary;
  record Tick(String id, String nominal) {}
  record Counter(String id, int count, String last) {}
  record Ping() {}
  private static final UserContext SERVICE = new UserContext("scheduler", Set.of("service"), Map.of());
  private static final CalendarSchedule EVERY_MINUTE = CalendarSchedule.fromAzureAttributes(Map.of("minute", "*"));
  private static Booster runtime(EventStore journal, StateStore state) {
    return Booster.builder(journal).stateStore(state).entity(Counter.class)
        .event(Tick.class, Tick::id, Counter.class,
            (event, previous) -> new Counter(event.id(), previous == null ? 1 : previous.count() + 1, event.nominal())).build();
  }
  private static ScheduledTask task(String name, ScheduledTask.Handler handler) {
    return new ScheduledTask(name, EVERY_MINUTE, Json.MAPPER.createObjectNode().put("version", 1), SERVICE, handler);
  }
  private static PersistentScheduler scheduler(Booster booster, List<ScheduledTask> tasks, Clock clock) {
    return new PersistentScheduler(booster, tasks, clock, Duration.ofSeconds(10), ignored -> {});
  }

  @Test void durableCursorCatchesUpAcrossDatabaseAndRuntimeRestart() {
    var clock = new MutableClock("2026-11-04T12:00:30Z");
    var calls = new AtomicInteger();
    var tick = task("tick", (occurrence, register) -> {
      calls.incrementAndGet(); register.emit(new Tick("counter", occurrence.scheduledAt().toString()));
    });
    try (var booster = runtime(new JdbcEventStore(temporary.resolve("journal")), new JdbcStateStore(temporary.resolve("state")));
         var scheduler = scheduler(booster, List.of(tick), clock)) {
      assertEquals(Instant.parse("2026-11-04T12:01:00Z"), scheduler.status("tick").nextAt());
      clock.set("2026-11-04T12:03:00Z");
      var result = scheduler.runDue(2); assertTrue(result.failures().isEmpty()); assertEquals(2, result.completed());
      assertEquals(Instant.parse("2026-11-04T12:03:00Z"), scheduler.status("tick").nextAt());
    }
    clock.set("2026-11-04T12:04:00Z");
    try (var booster = runtime(new JdbcEventStore(temporary.resolve("journal")), new JdbcStateStore(temporary.resolve("state")));
         var scheduler = scheduler(booster, List.of(tick), clock)) {
      assertEquals(2, scheduler.runDue(10).completed()); assertEquals(0, scheduler.runDue(10).completed());
      assertEquals(4, booster.entity(Counter.class, "counter").count());
      assertEquals(4, scheduler.status("tick").completed());
      assertEquals(Instant.parse("2026-11-04T12:05:00Z"), scheduler.status("tick").nextAt());
      assertNull(scheduler.status("tick").leaseOwner());
    }
    assertEquals(4, calls.get());
  }

  @Test void concurrentReplicasLeaseTheOccurrenceAndManualReplayUsesItsReceipt() throws Exception {
    var journal = new InMemoryEventStore(); var state = new InMemoryStateStore(); var clock = new MutableClock("2026-11-04T12:00:30Z");
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var calls = new AtomicInteger();
    var tick = task("tick", (occurrence, register) -> {
      calls.incrementAndGet(); entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
      register.emit(new Tick("counter", occurrence.scheduledAt().toString()));
    });
    try (var a = runtime(journal, state); var b = runtime(journal, state);
         var first = scheduler(a, List.of(tick), clock); var second = scheduler(b, List.of(tick), clock);
         var worker = Executors.newSingleThreadExecutor()) {
      clock.set("2026-11-04T12:01:00Z");
      var pending = worker.submit(() -> first.runDue(1));
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS)); assertEquals(0, second.runDue(1).completed());
        assertThrows(OptimisticConcurrencyException.class, () -> second.reconfigure(tick, "2", PersistentScheduler.CursorPolicy.FROM_NOW));
      }
      finally { release.countDown(); }
      assertEquals(1, pending.get(5, TimeUnit.SECONDS).completed());
      second.dispatch("tick", Instant.parse("2026-11-04T12:01:00Z"));
      assertEquals(1, calls.get()); assertEquals(1, journal.lastPosition());
    }
  }

  @Test void committedOccurrenceIsNotRepeatedIfCursorWriteFails() {
    var delegate = new InMemoryStateStore(); var fail = new AtomicBoolean(true);
    StateStore flaky = new StateStore() {
      public Optional<StateDocument> get(String namespace, String key) { return delegate.get(namespace, key); }
      public List<StateDocument> scan(String namespace, String after, int limit) { return delegate.scan(namespace, after, limit); }
      public StateDocument put(String namespace, String key, long version, JsonNode value) {
        if (namespace.equals("booster.schedules") && value.path("lastAt").isTextual() && fail.compareAndSet(true, false))
          throw new IllegalStateException("Cursor write unavailable");
        return delegate.put(namespace, key, version, value);
      }
    };
    var clock = new MutableClock("2026-11-04T12:00:30Z"); var calls = new AtomicInteger();
    var tick = task("tick", (occurrence, register) -> { calls.incrementAndGet(); register.emit(new Tick("counter", occurrence.scheduledAt().toString())); });
    try (var booster = runtime(new InMemoryEventStore(), flaky); var scheduler = scheduler(booster, List.of(tick), clock)) {
      clock.set("2026-11-04T12:01:00Z");
      assertEquals(1, scheduler.runDue(1).failures().size()); assertEquals(1, booster.position());
      assertEquals(1, scheduler.runDue(1).completed()); assertEquals(1, calls.get()); assertEquals(1, booster.position());
      assertEquals(Instant.parse("2026-11-04T12:02:00Z"), scheduler.status("tick").nextAt());
    }
  }

  @Test void handlerFailureRetainsOccurrenceAndDoesNotBlockIndependentSchedules() {
    var clock = new MutableClock("2026-11-04T12:00:30Z"); var attempts = new AtomicInteger();
    var bad = task("a-bad", (occurrence, register) -> {
      register.emit(new Tick("bad", occurrence.scheduledAt().toString()));
      if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Retry me");
    });
    var good = task("z-good", (occurrence, register) -> register.emit(new Tick("good", occurrence.scheduledAt().toString())));
    try (var booster = runtime(new InMemoryEventStore(), new InMemoryStateStore()); var scheduler = scheduler(booster, List.of(bad, good), clock)) {
      clock.set("2026-11-04T12:01:00Z");
      var failed = scheduler.runDue(10); assertEquals(1, failed.completed()); assertTrue(failed.failures().containsKey("a-bad"));
      assertNull(booster.entity(Counter.class, "bad")); assertEquals(1, booster.entity(Counter.class, "good").count());
      assertNotNull(scheduler.status("a-bad").lastFailure());
      assertEquals(1, scheduler.runDue(10).completed()); assertEquals(1, booster.entity(Counter.class, "bad").count());
      assertNull(scheduler.status("a-bad").lastFailure());
    }
  }

  @Test void manualOutOfOrderTimerDoesNotSkipEarlierOccurrencesAndRejectsFutureTimes() {
    var clock = new MutableClock("2026-11-04T12:00:30Z"); var calls = new AtomicInteger();
    var tick = task("tick", (occurrence, register) -> { calls.incrementAndGet(); register.emit(new Tick("counter", occurrence.scheduledAt().toString())); });
    try (var booster = runtime(new InMemoryEventStore(), new InMemoryStateStore()); var scheduler = scheduler(booster, List.of(tick), clock)) {
      clock.set("2026-11-04T12:03:00Z");
      scheduler.dispatch("tick", Instant.parse("2026-11-04T12:03:00Z"));
      assertEquals(Instant.parse("2026-11-04T12:01:00Z"), scheduler.status("tick").nextAt());
      assertEquals(3, scheduler.runDue(10).completed()); assertEquals(3, calls.get()); assertEquals(3, booster.position());
      assertThrows(IllegalArgumentException.class, () -> scheduler.dispatch("tick", Instant.parse("2026-11-04T12:04:00Z")));
      assertThrows(IllegalArgumentException.class, () -> scheduler.dispatch("tick", Instant.parse("2026-11-04T12:02:01Z")));
    }
  }

  @Test void expiredLeaseTakeoverCannotDuplicateBufferedEvents() throws Exception {
    var journal = new InMemoryEventStore(); var state = new InMemoryStateStore(); var clock = new MutableClock("2026-11-04T12:00:30Z");
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var calls = new AtomicInteger();
    var tick = task("tick", (occurrence, register) -> {
      register.emit(new Tick("counter", occurrence.scheduledAt().toString()));
      if (calls.incrementAndGet() == 1) { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
    });
    try (var a = runtime(journal, state); var b = runtime(journal, state);
         var first = new PersistentScheduler(a, List.of(tick), clock, Duration.ofSeconds(1), ignored -> {});
         var second = new PersistentScheduler(b, List.of(tick), clock, Duration.ofSeconds(1), ignored -> {});
         var worker = Executors.newSingleThreadExecutor()) {
      clock.set("2026-11-04T12:01:00Z"); var pending = worker.submit(() -> first.runDue(1));
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS)); clock.set("2026-11-04T12:01:02Z");
        assertEquals(1, second.runDue(1).completed());
      } finally { release.countDown(); }
      pending.get(5, TimeUnit.SECONDS);
      assertEquals(2, calls.get()); assertEquals(1, journal.lastPosition()); assertEquals(1, b.entity(Counter.class, "counter").count());
      assertEquals(Instant.parse("2026-11-04T12:02:00Z"), second.status("tick").nextAt());
    }
  }

  @Test void backgroundPollingExecutesWithoutAnExternalTimerService() throws Exception {
    var happened = new CountDownLatch(1);
    var state = new InMemoryStateStore();
    StateStore observed = new StateStore() {
      public Optional<StateDocument> get(String namespace, String key) { return state.get(namespace, key); }
      public List<StateDocument> scan(String namespace, String after, int limit) { return state.scan(namespace, after, limit); }
      public StateDocument put(String namespace, String key, long version, JsonNode value) {
        StateDocument committed = state.put(namespace, key, version, value);
        if (namespace.equals("booster.schedules") && value.path("lastAt").isTextual()) happened.countDown();
        return committed;
      }
    };
    var tick = task("tick", (occurrence, register) -> register.emit(new Tick("background", occurrence.scheduledAt().toString())));
    try (var booster = Booster.builder(new InMemoryEventStore()).stateStore(observed)
        .entity(Counter.class).event(Tick.class, Tick::id, Counter.class,
            (event, previous) -> new Counter(event.id(), previous == null ? 1 : previous.count() + 1, event.nominal())).build();
         var scheduler = scheduler(booster, List.of(tick), Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC))) {
      scheduler.start(Duration.ofMillis(10), 1);
      assertTrue(happened.await(5, TimeUnit.SECONDS));
      assertEquals(1, booster.position()); assertEquals(1, booster.entity(Counter.class, "background").count());
      assertThrows(IllegalStateException.class, () -> scheduler.start(Duration.ofMillis(10), 1));
    }
  }

  @Test void disabledSchedulesStayDisabledAndChangedDefinitionsDoNotResetProgress() {
    var journal = new InMemoryEventStore(); var state = new InMemoryStateStore(); var clock = new MutableClock("2026-11-04T12:00:30Z");
    var disabled = new ScheduledTask("disabled", CalendarSchedule.fromAzureAttributes(Map.of()), null, SERVICE, (occurrence, register) -> fail("disabled"));
    var tick = task("tick", (occurrence, register) -> {});
    try (var booster = runtime(journal, state); var scheduler = scheduler(booster, List.of(tick, disabled), clock)) {
      clock.set("2026-11-04T12:01:00Z"); assertEquals(1, scheduler.runDue(10).completed()); assertNull(scheduler.status("disabled").nextAt());
      var changed = new ScheduledTask("tick", new CalendarSchedule("*/2", "*", "*", "*", "*", "*"), tick.input(), SERVICE, tick.handler());
      assertThrows(IllegalStateException.class, () -> scheduler(booster, List.of(changed), clock));
      assertEquals(Instant.parse("2026-11-04T12:02:00Z"), scheduler.status("tick").nextAt());
    }
  }

  @Test void boundedRunsRotateBetweenSchedulesInsteadOfStarvingLaterNames() {
    var clock = new MutableClock("2026-11-04T12:00:30Z");
    var first = task("a", (occurrence, register) -> register.emit(new Tick("a", occurrence.scheduledAt().toString())));
    var last = task("z", (occurrence, register) -> register.emit(new Tick("z", occurrence.scheduledAt().toString())));
    try (var booster = runtime(new InMemoryEventStore(), new InMemoryStateStore()); var scheduler = scheduler(booster, List.of(first, last), clock)) {
      clock.set("2026-11-04T12:03:00Z"); scheduler.runDue(1); scheduler.runDue(1);
      assertEquals(1, booster.entity(Counter.class, "a").count()); assertEquals(1, booster.entity(Counter.class, "z").count());
    }
  }

  @Test void explicitSameNameUpdateHasANewReceiptGenerationAndIsIdempotent() {
    var clock = new MutableClock("2026-11-04T12:00:30Z");
    var tick = task("tick", (occurrence, register) -> register.emit(new Tick("counter", occurrence.generation() + ":" + occurrence.input().path("version").asInt())));
    var replacement = new ScheduledTask("tick", EVERY_MINUTE, Json.MAPPER.createObjectNode().put("version", 2), SERVICE, tick.handler());
    try (var booster = runtime(new InMemoryEventStore(), new InMemoryStateStore()); var scheduler = scheduler(booster, List.of(tick), clock)) {
      clock.set("2026-11-04T12:01:00Z"); assertEquals(1, scheduler.runDue(1).completed());
      assertEquals("1:1", booster.entity(Counter.class, "counter").last());
      clock.set("2026-11-04T12:01:30Z");
      scheduler.reconfigure(replacement, "v2", PersistentScheduler.CursorPolicy.FROM_NOW);
      assertEquals("v2", scheduler.status("tick").generation());
      assertEquals(Instant.parse("2026-11-04T12:02:00Z"), scheduler.status("tick").nextAt());
      PersistentScheduler.reconfigure(booster, replacement, "1", "v2", PersistentScheduler.CursorPolicy.FROM_NOW, clock);
      assertThrows(IllegalArgumentException.class, () -> scheduler.dispatch("tick", Instant.parse("2026-11-04T12:01:00Z")));
      clock.set("2026-11-04T12:02:00Z"); assertEquals(1, scheduler.runDue(1).completed());
      assertEquals(2, booster.entity(Counter.class, "counter").count()); assertEquals("v2:2", booster.entity(Counter.class, "counter").last());
      assertThrows(OptimisticConcurrencyException.class, () -> PersistentScheduler.reconfigure(booster, replacement, "1", "v3", PersistentScheduler.CursorPolicy.FROM_NOW, clock));
      assertThrows(IllegalArgumentException.class, () -> scheduler.reconfigure(tick, "1", PersistentScheduler.CursorPolicy.FROM_NOW));
      assertThrows(IllegalArgumentException.class, () -> scheduler.reconfigure(tick, "legacy", PersistentScheduler.CursorPolicy.FROM_NOW));
    }
  }

  @Test void afterLastCompletedUpdateKeepsBacklogAndFencesStaleReplicas() {
    var journal = new InMemoryEventStore(); var state = new InMemoryStateStore(); var clock = new MutableClock("2026-11-04T12:00:30Z");
    var tick = task("tick", (occurrence, register) -> register.emit(new Tick("counter", occurrence.scheduledAt().toString())));
    var replacement = new ScheduledTask("tick", CalendarSchedule.fromAzureAttributes(Map.of("minute", "*/2")), tick.input(), SERVICE, tick.handler());
    try (var a = runtime(journal, state); var b = runtime(journal, state);
         var current = scheduler(a, List.of(tick), clock); var stale = scheduler(b, List.of(tick), clock)) {
      clock.set("2026-11-04T12:01:00Z"); assertEquals(1, current.runDue(1).completed());
      clock.set("2026-11-04T12:04:00Z"); current.reconfigure(replacement, "2", PersistentScheduler.CursorPolicy.AFTER_LAST_COMPLETED);
      assertEquals(Instant.parse("2026-11-04T12:02:00Z"), current.status("tick").nextAt());
      assertTrue(stale.runDue(10).failures().containsKey("tick"));
      assertEquals(2, current.runDue(10).completed()); assertEquals(3, a.entity(Counter.class, "counter").count());
      try (var restarted = scheduler(b, List.of(replacement), clock)) {
        assertEquals("2", restarted.status("tick").generation()); assertEquals(0, restarted.runDue(10).completed());
      }
    }
  }

  @Test void updateRecoversACommittedOldOccurrenceBeforeChoosingTheNewCursor() {
    var state = new InMemoryStateStore(); var fail = new AtomicBoolean(true);
    StateStore flaky = new StateStore() {
      public Optional<StateDocument> get(String namespace, String key) { return state.get(namespace, key); }
      public List<StateDocument> scan(String namespace, String after, int limit) { return state.scan(namespace, after, limit); }
      public StateDocument put(String namespace, String key, long version, JsonNode value) {
        if (namespace.equals("booster.schedules") && value.path("lastAt").isTextual() && fail.compareAndSet(true, false))
          throw new IllegalStateException("Crash after journal commit");
        return state.put(namespace, key, version, value);
      }
    };
    var clock = new MutableClock("2026-11-04T12:00:30Z"); var calls = new AtomicInteger();
    var tick = task("tick", (occurrence, register) -> { calls.incrementAndGet(); register.emit(new Tick("counter", occurrence.scheduledAt().toString())); });
    try (var booster = runtime(new InMemoryEventStore(), flaky); var scheduler = scheduler(booster, List.of(tick), clock)) {
      clock.set("2026-11-04T12:01:00Z"); assertTrue(scheduler.runDue(1).failures().containsKey("tick"));
      scheduler.reconfigure(tick, "2", PersistentScheduler.CursorPolicy.AFTER_LAST_COMPLETED);
      assertEquals(Instant.parse("2026-11-04T12:02:00Z"), scheduler.status("tick").nextAt());
      assertEquals(1, scheduler.status("tick").completed()); assertEquals(1, calls.get()); assertEquals(1, booster.position());
    }
  }

  @Test void publicCommandsCannotPreclaimNominalTimerReceipts() throws Exception {
    var clock = new MutableClock("2026-11-04T12:00:30Z"); var calls = new AtomicInteger();
    var tick = task("tick", (occurrence, register) -> calls.incrementAndGet());
    String receipt = "__booster_schedule:" + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
        .digest("tick\n1\n2026-11-04T12:01:00Z".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    try (var booster = Booster.builder(new InMemoryEventStore()).stateStore(new InMemoryStateStore())
        .command(Ping.class, String.class, AccessPolicy.publicAccess(), (input, register) -> "pong").build();
         var scheduler = scheduler(booster, List.of(tick), clock)) {
      var rejected = assertThrows(BoosterException.class, () -> booster.executeCommand("Ping", Json.MAPPER.createObjectNode(),
          new RequestContext(receipt, UserContext.anonymous(), Map.of())));
      assertEquals(BoosterException.Code.VALIDATION, rejected.code());
      clock.set("2026-11-04T12:01:00Z"); assertEquals(1, scheduler.runDue(1).completed()); assertEquals(1, calls.get());
      assertTrue(booster.eventStore().findCommand(receipt).isPresent());
    }
  }

  @Test void consecutiveUpdatesCannotResurrectABacklogRetiredByAnEarlierGeneration() {
    var clock = new MutableClock("2026-11-04T12:00:30Z");
    var tick = task("tick", (occurrence, register) -> register.emit(new Tick("counter", occurrence.scheduledAt().toString())));
    try (var booster = runtime(new InMemoryEventStore(), new InMemoryStateStore()); var scheduler = scheduler(booster, List.of(tick), clock)) {
      clock.set("2026-11-04T12:01:00Z"); assertEquals(1, scheduler.runDue(1).completed());
      clock.set("2026-11-04T12:10:00Z");
      scheduler.reconfigure(tick, "2", PersistentScheduler.CursorPolicy.FROM_NOW);
      scheduler.reconfigure(tick, "3", PersistentScheduler.CursorPolicy.AFTER_LAST_COMPLETED);
      assertEquals(Instant.parse("2026-11-04T12:11:00Z"), scheduler.status("tick").nextAt());
      var disabled = new ScheduledTask("tick", CalendarSchedule.fromAzureAttributes(Map.of()), tick.input(), SERVICE, tick.handler());
      scheduler.reconfigure(disabled, "4", PersistentScheduler.CursorPolicy.AFTER_LAST_COMPLETED);
      scheduler.reconfigure(tick, "5", PersistentScheduler.CursorPolicy.AFTER_LAST_COMPLETED);
      assertEquals(Instant.parse("2026-11-04T12:11:00Z"), scheduler.status("tick").nextAt());
      assertEquals(0, scheduler.runDue(10).completed()); assertEquals(1, booster.position());
    }
  }

  @Test void shutdownDuringLeaseConstructionReleasesTheAlreadyCommittedOwner() throws Exception {
    var delegate = new InMemoryStateStore(); var acquired = new CountDownLatch(1); var release = new CountDownLatch(1);
    var intercept = new AtomicBoolean(true);
    StateStore delayed = new StateStore() {
      public Optional<StateDocument> get(String namespace, String key) { return delegate.get(namespace, key); }
      public List<StateDocument> scan(String namespace, String after, int limit) { return delegate.scan(namespace, after, limit); }
      public StateDocument put(String namespace, String key, long version, JsonNode value) {
        StateDocument committed = delegate.put(namespace, key, version, value);
        if (namespace.equals("booster.schedules") && value.path("owner").isTextual() && intercept.compareAndSet(true, false)) {
          acquired.countDown();
          try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test did not release acquired lease"); }
          catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        }
        return committed;
      }
    };
    var clock = new MutableClock("2026-11-04T12:00:30Z");
    var tick = task("tick", (occurrence, register) -> fail("A closed scheduler must not execute the callback"));
    try (var booster = runtime(new InMemoryEventStore(), delayed); var scheduler = scheduler(booster, List.of(tick), clock);
         var worker = Executors.newSingleThreadExecutor()) {
      clock.set("2026-11-04T12:01:00Z"); var pending = worker.submit(() -> scheduler.runDue(1));
      try { assertTrue(acquired.await(5, TimeUnit.SECONDS)); scheduler.close(); }
      finally { release.countDown(); }
      pending.get(5, TimeUnit.SECONDS);
      assertNull(PersistentScheduler.inspect(booster, "tick").leaseOwner());
      PersistentScheduler.reconfigure(booster, tick, "1", "2", PersistentScheduler.CursorPolicy.FROM_NOW, clock);
      assertEquals("2", PersistentScheduler.inspect(booster, "tick").generation());
    }
  }

  @Test void scheduleErrorSuppressionCommitsBufferedEventsWithoutRunningCommandHooks() {
    var clock = new MutableClock("2026-11-04T12:00:30Z"); var errors = new AtomicInteger();
    var tick = task("tick", (occurrence, register) -> { register.emit(new Tick("suppressed", occurrence.scheduledAt().toString())); throw new IllegalStateException("application failure"); });
    try (var booster = Booster.builder(new InMemoryEventStore()).stateStore(new InMemoryStateStore())
        .entity(Counter.class).event(Tick.class, Tick::id, Counter.class,
            (event, previous) -> new Counter(event.id(), previous == null ? 1 : previous.count() + 1, event.nominal()))
        .beforeCommand((name, register) -> fail("Scheduled tasks do not use command hooks"))
        .onError(FrameworkError.Stage.COMMAND, error -> { fail("Scheduled errors do not use the command boundary"); return error.error(); })
        .onError(FrameworkError.Stage.SCHEDULE, error -> {
          assertEquals("scheduled-task:tick", error.operation());
          assertEquals("scheduler", error.context().user().subject());
          assertEquals("2026-11-04T12:01:00Z", error.context().metadata().get("scheduledAt"));
          errors.incrementAndGet(); return null;
        }).build(); var scheduler = scheduler(booster, List.of(tick), clock)) {
      clock.set("2026-11-04T12:01:00Z");
      var result = scheduler.runDue(1); assertTrue(result.failures().isEmpty()); assertEquals(1, result.completed());
      assertEquals(1, booster.entity(Counter.class, "suppressed").count()); assertEquals(1, errors.get());
      scheduler.dispatch("tick", Instant.parse("2026-11-04T12:01:00Z"));
      assertEquals(1, errors.get()); assertEquals(1, booster.position());
    }
  }

  @Test void scheduleErrorReplacementRollsBackAndKeepsTheOccurrenceDue() {
    var clock = new MutableClock("2026-11-04T12:00:30Z");
    var tick = task("tick", (occurrence, register) -> { register.emit(new Tick("replaced", occurrence.scheduledAt().toString())); throw new IllegalStateException("application failure"); });
    try (var booster = Booster.builder(new InMemoryEventStore()).stateStore(new InMemoryStateStore())
        .entity(Counter.class).event(Tick.class, Tick::id, Counter.class,
            (event, previous) -> new Counter(event.id(), previous == null ? 1 : previous.count() + 1, event.nominal()))
        .onError(FrameworkError.Stage.SCHEDULE, error -> new BoosterException(BoosterException.Code.FORBIDDEN, "replacement failure"))
        .build(); var scheduler = scheduler(booster, List.of(tick), clock)) {
      clock.set("2026-11-04T12:01:00Z");
      var result = scheduler.runDue(1); assertEquals(0, result.completed());
      assertTrue(result.failures().get("tick").contains("replacement failure"));
      assertEquals(0, booster.position()); assertNull(booster.entity(Counter.class, "replaced"));
      assertEquals(Instant.parse("2026-11-04T12:01:00Z"), scheduler.status("tick").nextAt());
    }
  }

  @Test void applicationErrorSuppressionCannotSuppressTheLeaseCommitGuard() {
    var clock = new MutableClock("2026-11-04T12:00:30Z"); var errors = new AtomicInteger();
    var tick = task("tick", (occurrence, register) -> {
      register.emit(new Tick("guarded", occurrence.scheduledAt().toString()));
      clock.set("2026-11-04T12:01:20Z"); throw new IllegalStateException("application error after lease expiry");
    });
    try (var booster = Booster.builder(new InMemoryEventStore()).stateStore(new InMemoryStateStore())
        .entity(Counter.class).event(Tick.class, Tick::id, Counter.class,
            (event, previous) -> new Counter(event.id(), previous == null ? 1 : previous.count() + 1, event.nominal()))
        .onError(FrameworkError.Stage.SCHEDULE, error -> { errors.incrementAndGet(); return null; }).build();
         var scheduler = scheduler(booster, List.of(tick), clock)) {
      clock.set("2026-11-04T12:01:00Z");
      var result = scheduler.runDue(1); assertEquals(0, result.completed()); assertEquals(1, result.failures().size());
      assertEquals(1, errors.get()); assertEquals(0, booster.position());
      assertNull(booster.entity(Counter.class, "guarded"));
      assertEquals(Instant.parse("2026-11-04T12:01:00Z"), scheduler.status("tick").nextAt());
    }
  }

  @Test void explicitFlushCannotPersistEventsAfterTheScheduleLeaseExpires() {
    var clock = new MutableClock("2026-11-04T12:00:30Z");
    var tick = task("tick", (occurrence, register) -> {
      register.emit(new Tick("late-flush", occurrence.scheduledAt().toString()));
      clock.set("2026-11-04T12:01:20Z"); register.flush();
    });
    try (var booster = Booster.builder(new InMemoryEventStore()).stateStore(new InMemoryStateStore())
        .entity(Counter.class).event(Tick.class, Tick::id, Counter.class,
            (event, previous) -> new Counter(event.id(), previous == null ? 1 : previous.count() + 1, event.nominal()))
        .onError(FrameworkError.Stage.SCHEDULE, error -> null).build();
         var scheduler = scheduler(booster, List.of(tick), clock)) {
      clock.set("2026-11-04T12:01:00Z");
      assertEquals(1, scheduler.runDue(1).failures().size());
      assertEquals(0, booster.position()); assertNull(booster.entity(Counter.class, "late-flush"));
    }
  }

  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> value;
    MutableClock(String initial) { value = new AtomicReference<>(Instant.parse(initial)); }
    void set(String instant) { value.set(Instant.parse(instant)); }
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException(); return this; }
    public Instant instant() { return value.get(); }
  }
}
