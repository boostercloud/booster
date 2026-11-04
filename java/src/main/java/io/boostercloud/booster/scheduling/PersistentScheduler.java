package io.boostercloud.booster.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.boostercloud.booster.core.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Calendar scheduler with durable occurrence cursors and renewable per-schedule CAS leases.
 * A committed Booster receipt, not a process-local timer, deduplicates nominal occurrences.
 */
public final class PersistentScheduler implements AutoCloseable {
  private static final String NAMESPACE = "booster.schedules";
  private static final int CAS_ATTEMPTS = 32;
  private final Booster booster;
  private final StateStore state;
  private final Map<String, ScheduledTask> tasks;
  private final Map<String, String> generations = new ConcurrentHashMap<>();
  private final Clock clock;
  private final Duration leaseDuration;
  private final Consumer<Throwable> errorHandler;
  private final Set<Thread> timerThreads = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2,
      runnable -> {
        Thread thread = Thread.ofPlatform().daemon().name("booster-calendar").unstarted(runnable);
        timerThreads.add(thread); return thread;
      });
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean started = new AtomicBoolean();
  private final AtomicInteger firstTask = new AtomicInteger();

  public PersistentScheduler(Booster booster, List<ScheduledTask> tasks) {
    this(booster, tasks, Clock.systemUTC(), Duration.ofMinutes(1), error ->
        System.getLogger(PersistentScheduler.class.getName()).log(System.Logger.Level.WARNING,
            error.getClass().getSimpleName() + ": " + Objects.toString(error.getMessage(), "Scheduled work failed")));
  }
  public PersistentScheduler(Booster booster, List<ScheduledTask> configured, Clock clock,
      Duration leaseDuration, Consumer<Throwable> errorHandler) {
    this.booster = Objects.requireNonNull(booster); state = booster.stateStore();
    this.clock = Objects.requireNonNull(clock); this.leaseDuration = Objects.requireNonNull(leaseDuration);
    this.errorHandler = Objects.requireNonNull(errorHandler);
    if (leaseDuration.compareTo(Duration.ofMillis(300)) < 0 || leaseDuration.compareTo(Duration.ofHours(1)) > 0)
      throw new IllegalArgumentException("Schedule leases must last 300 ms to one hour");
    var definitions = new TreeMap<String, ScheduledTask>();
    for (ScheduledTask task : configured) if (definitions.putIfAbsent(task.name(), task) != null)
      throw new IllegalArgumentException("Duplicate schedule: " + task.name());
    tasks = new ConcurrentSkipListMap<>(definitions);
    try { tasks.values().forEach(this::ensureState); }
    catch (RuntimeException error) { timers.shutdownNow(); throw error; }
  }

  /** Poll due calendars in this Java host. Multiple hosts safely share the same durable state/journal. */
  public void start(Duration pollInterval, int maxOccurrencesPerPoll) {
    checkOpen();
    if (pollInterval == null || pollInterval.toMillis() < 10 || pollInterval.compareTo(Duration.ofMinutes(1)) > 0)
      throw new IllegalArgumentException("Polling interval must be between 10 ms and one minute");
    validateLimit(maxOccurrencesPerPoll);
    if (!started.compareAndSet(false, true)) throw new IllegalStateException("Scheduler is already started");
    timers.scheduleWithFixedDelay(() -> {
      try { runDue(maxOccurrencesPerPoll); } catch (RuntimeException error) { report(error); }
    }, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** Execute a bounded backlog in nominal order per schedule; failures remain due for the next attempt. */
  public RunResult runDue(int maxOccurrences) {
    checkOpen(); validateLimit(maxOccurrences);
    int completed = 0;
    var failures = new LinkedHashMap<String, String>();
    var ordered = new ArrayList<>(tasks.values());
    if (!ordered.isEmpty()) Collections.rotate(ordered, -Math.floorMod(firstTask.getAndIncrement(), ordered.size()));
    for (ScheduledTask task : ordered) {
      if (completed >= maxOccurrences || closed.get()) break;
      try (Lease lease = acquire(task)) {
        if (lease == null) continue;
        while (completed < maxOccurrences && !closed.get()) {
          JsonNode document = lease.current();
          if (!document.path("nextAt").isTextual()) break;
          Instant scheduledAt = Instant.parse(document.get("nextAt").asText());
          if (scheduledAt.isAfter(clock.instant())) break;
          execute(task, scheduledAt, lease);
          advance(task, scheduledAt, lease);
          completed++;
        }
      } catch (RuntimeException error) {
        failures.put(task.name(), error.getClass().getSimpleName() + ": " + Objects.toString(error.getMessage(), "Execution failed"));
        report(error);
      }
    }
    return new RunResult(completed, failures);
  }

  /** An Azure timer/custom trigger can dispatch the same nominal occurrence without creating a second identity. */
  public void dispatch(String name, Instant scheduledAt) {
    checkOpen(); ScheduledTask task = requireTask(name); Objects.requireNonNull(scheduledAt);
    if (!task.schedule().matches(scheduledAt)) throw new IllegalArgumentException("Nominal time does not match the calendar");
    if (scheduledAt.isAfter(clock.instant())) throw new IllegalArgumentException("Cannot dispatch a future occurrence");
    try (Lease lease = acquire(task)) {
      if (lease == null) throw new OptimisticConcurrencyException("Schedule is leased by another worker: " + name);
      execute(task, scheduledAt, lease);
      advance(task, scheduledAt, lease);
    }
  }

  public Status status(String name) {
    checkOpen(); requireTask(name);
    return inspect(booster, name);
  }
  public static Status inspect(Booster booster, String name) {
    JsonNode value = booster.stateStore().get(NAMESPACE, key(name)).orElseThrow(() -> new IllegalArgumentException("Unknown schedule: " + name)).value();
    return new Status(name, instant(value, "nextAt"), instant(value, "lastAt"), value.path("completed").asLong(),
        value.path("attempts").asLong(), value.path("lastFailure").isTextual() ? value.get("lastFailure").asText() : null,
        value.path("owner").isTextual() ? value.get("owner").asText() : null, instant(value, "leaseUntil"), generation(value));
  }
  public record RunResult(int completed, Map<String, String> failures) {
    public RunResult { failures = Map.copyOf(failures); }
  }
  public record Status(String name, Instant nextAt, Instant lastAt, long completed, long attempts,
      String lastFailure, String leaseOwner, Instant leaseUntil, String generation) {}

  public enum CursorPolicy {
    /** Intentionally retire the old backlog and start strictly after the update's clock time. */
    FROM_NOW,
    /** Apply the new calendar after the last completed occurrence, retaining the outstanding time range. */
    AFTER_LAST_COMPLETED
  }

  /** Update this worker and its durable definition after all current work has released its lease. */
  public synchronized void reconfigure(ScheduledTask replacement, String newGeneration, CursorPolicy policy) {
    checkOpen(); requireTask(replacement.name());
    reconfigure(booster, replacement, generations.get(replacement.name()), newGeneration, policy, clock);
    tasks.put(replacement.name(), replacement); generations.put(replacement.name(), newGeneration);
  }
  public static void reconfigure(Booster booster, ScheduledTask replacement, String expectedGeneration,
      String newGeneration, CursorPolicy policy) {
    reconfigure(booster, replacement, expectedGeneration, newGeneration, policy, Clock.systemUTC());
  }
  /** Bootstrap/deployment API; repeat the same generation/configuration/policy safely before constructing a new worker. */
  public static void reconfigure(Booster booster, ScheduledTask replacement, String expectedGeneration,
      String newGeneration, CursorPolicy policy, Clock clock) {
    Objects.requireNonNull(booster); Objects.requireNonNull(replacement); Objects.requireNonNull(policy); Objects.requireNonNull(clock);
    if (expectedGeneration == null || newGeneration == null || newGeneration.equals("legacy") || !newGeneration.matches("[A-Za-z0-9._:-]{1,128}"))
      throw new IllegalArgumentException("Expected and new stable schedule generations are required");
    StateStore state = booster.stateStore();
    for (int i = 0; i < CAS_ATTEMPTS; i++) {
      StateDocument document = state.get(NAMESPACE, key(replacement.name())).orElseThrow(() -> new IllegalArgumentException("Schedule is not registered"));
      ObjectNode value = object(document.value()); String currentGeneration = generation(value);
      if (currentGeneration.equals(newGeneration)) {
        if (configuration(replacement).equals(value.path("configuration").asText())
            && policy.name().equals(value.path("lastReconfiguration").path("policy").asText())) return;
        throw new IllegalStateException("Schedule generation already identifies another configuration/update policy");
      }
      if (!currentGeneration.equals(expectedGeneration)) throw new OptimisticConcurrencyException("Schedule generation changed: " + replacement.name());
      // Even an expired owner must first be recovered by the old schedule worker. This
      // prevents a deployment from abandoning a possibly committed old occurrence.
      if (value.path("owner").isTextual()) throw new OptimisticConcurrencyException("Stop/recover the leased schedule before reconfiguring: " + replacement.name());
      var used = Json.MAPPER.createArrayNode();
      if (value.path("generations").isArray()) value.get("generations").forEach(used::add);
      else used.add(currentGeneration);
      for (JsonNode old : used) if (newGeneration.equals(old.asText())) throw new IllegalArgumentException("A retired schedule generation cannot be reused");
      if (used.size() >= 1024) throw new IllegalStateException("Use a new schedule name after 1024 generations");
      used.add(newGeneration);
      Instant previousNext = instant(value, "nextAt"); Instant completed = instant(value, "lastAt");
      if (currentGeneration.equals("legacy") && previousNext != null && !previousNext.isAfter(clock.instant()))
        throw new IllegalStateException("Recover due legacy occurrences before reconfiguring the schedule");
      // Recover the commit/cursor-write crash window before selecting an update cursor.
      if (!currentGeneration.equals("legacy") && previousNext != null
          && booster.eventStore().findCommand(receiptId(replacement.name(), currentGeneration, previousNext)).isPresent()) {
        completed = previousNext; value.put("lastAt", completed.toString());
        value.put("completed", Math.addExact(value.path("completed").asLong(), 1));
      }
      Instant reference = policy == CursorPolicy.FROM_NOW ? clock.instant()
          : completed != null ? completed : previousNext != null ? previousNext.minusNanos(1) : clock.instant();
      Instant previousBoundary = instant(value, "generationAfter");
      if (previousBoundary != null && reference.isBefore(previousBoundary)) reference = previousBoundary;
      Optional<Instant> next = replacement.schedule().nextAfter(reference);
      if (next.isPresent()) value.put("nextAt", next.get().toString()); else value.putNull("nextAt");
      var change = Json.MAPPER.createObjectNode().put("from", currentGeneration).put("to", newGeneration)
          .put("policy", policy.name()).put("at", clock.instant().toString());
      if (previousNext != null) change.put("previousNextAt", previousNext.toString()); else change.putNull("previousNextAt");
      value.set("lastReconfiguration", change); value.set("generations", used);
      value.put("generation", newGeneration).put("generationAfter", reference.toString())
          .put("configuration", configuration(replacement)).putNull("lastFailure");
      try { state.put(NAMESPACE, key(replacement.name()), document.version(), value); return; }
      catch (OptimisticConcurrencyException raced) { /* A worker acquired the lease or another update won. */ }
    }
    throw new OptimisticConcurrencyException("Could not reconfigure schedule: " + replacement.name());
  }

  private void execute(ScheduledTask task, Instant scheduledAt, Lease lease) {
    Instant generationAfter = instant(lease.current(), "generationAfter");
    if (generationAfter != null && !scheduledAt.isAfter(generationAfter))
      throw new IllegalArgumentException("Occurrence predates this schedule generation");
    lease.update(value -> value.put("attempts", Math.addExact(value.path("attempts").asLong(), 1)));
    var input = Json.MAPPER.createObjectNode().put("schedule", task.name()).put("scheduledAt", scheduledAt.toString());
    input.set("input", task.input());
    String requestId = receiptId(task.name(), lease.generation, scheduledAt);
    var metadata = new HashMap<>(Map.of("transport", "calendar", "schedule", task.name(), "scheduledAt", scheduledAt.toString()));
    if (!lease.generation.equals("legacy")) metadata.put("generation", lease.generation);
    try {
      CommandHandler<JsonNode> handler = (ignored, register) -> {
            lease.assertOwned();
            task.handler().handle(new ScheduledTask.Occurrence(task.name(), lease.generation, scheduledAt, task.input()), register);
            lease.assertOwned(); return null;
          };
      if (lease.generation.equals("legacy"))
        booster.executeAction("scheduled-task:" + task.name(), input, new RequestContext(requestId, task.user(), metadata), AccessPolicy.publicAccess(), handler);
      else
        booster.executeSystemAction("scheduled-task:" + task.name(), input, new RequestContext(requestId, task.user(), metadata), AccessPolicy.publicAccess(), handler);
    } catch (RuntimeException error) {
      try {
        String reason = error.getClass().getSimpleName() + ": " + Objects.toString(error.getMessage(), "Execution failed");
        lease.update(value -> value.put("lastFailure", reason.substring(0, Math.min(reason.length(), 2048))));
      } catch (RuntimeException recordingError) { error.addSuppressed(recordingError); }
      throw error;
    }
  }
  private void advance(ScheduledTask task, Instant scheduledAt, Lease lease) {
    lease.update(value -> {
      // Manual out-of-order triggers must never skip earlier unprocessed occurrences.
      if (scheduledAt.toString().equals(value.path("nextAt").asText())) {
        Optional<Instant> next = task.schedule().nextAfter(scheduledAt);
        if (next.isPresent()) value.put("nextAt", next.get().toString()); else value.putNull("nextAt");
        value.put("lastAt", scheduledAt.toString()); value.put("completed", Math.addExact(value.path("completed").asLong(), 1));
      }
      return value.putNull("lastFailure");
    });
  }

  private void ensureState(ScheduledTask task) {
    String configuration = configuration(task);
    for (int i = 0; i < CAS_ATTEMPTS; i++) {
      var current = state.get(NAMESPACE, key(task.name()));
      if (current.isPresent() && !current.get().deleted()) {
        verifyConfiguration(task, current.get().value()); generations.put(task.name(), generation(current.get().value())); return;
      }
      var value = Json.MAPPER.createObjectNode().put("name", task.name()).put("configuration", configuration)
          .put("generation", "1").put("completed", 0).put("attempts", 0).putNull("lastAt").putNull("lastFailure").putNull("owner").putNull("leaseUntil");
      value.putArray("generations").add("1");
      Instant initial = clock.instant().minusNanos(1); value.put("generationAfter", initial.toString());
      Optional<Instant> next = task.schedule().nextAfter(initial);
      if (next.isPresent()) value.put("nextAt", next.get().toString()); else value.putNull("nextAt");
      try { state.put(NAMESPACE, key(task.name()), current.map(StateDocument::version).orElse(0L), value); generations.put(task.name(), "1"); return; }
      catch (OptimisticConcurrencyException raced) { /* Read the winner and verify its definition. */ }
    }
    throw new OptimisticConcurrencyException("Could not initialize schedule: " + task.name());
  }
  private synchronized Lease acquire(ScheduledTask task) {
    if (closed.get() || tasks.get(task.name()) != task) return null;
    for (int i = 0; i < CAS_ATTEMPTS; i++) {
      StateDocument document = state.get(NAMESPACE, key(task.name())).orElseThrow();
      ObjectNode value = object(document.value()); verifyConfiguration(task, value);
      Instant expiration = instant(value, "leaseUntil");
      if (value.path("owner").isTextual() && expiration != null && expiration.isAfter(clock.instant())) return null;
      String owner = UUID.randomUUID().toString();
      value.put("owner", owner).put("leaseUntil", clock.instant().plus(leaseDuration).toString());
      try {
        state.put(NAMESPACE, key(task.name()), document.version(), value);
        return new Lease(task, owner, generation(value));
      } catch (OptimisticConcurrencyException raced) { /* Retry the observed owner. */ }
    }
    throw new OptimisticConcurrencyException("Could not lease schedule: " + task.name());
  }
  private final class Lease implements AutoCloseable {
    private final ScheduledTask task;
    private final String owner;
    private final String generation;
    private final AtomicBoolean lost = new AtomicBoolean();
    private final AtomicBoolean released = new AtomicBoolean();
    private final ScheduledFuture<?> heartbeat;
    Lease(ScheduledTask task, String owner, String generation) {
      this.task = task; this.owner = owner; this.generation = generation;
      long period = Math.max(1, leaseDuration.toMillis() / 3);
      try {
        heartbeat = timers.scheduleWithFixedDelay(() -> {
          try { update(value -> value.put("leaseUntil", clock.instant().plus(leaseDuration).toString())); }
          catch (RuntimeException error) { lost.set(true); report(error); }
        }, period, period, TimeUnit.MILLISECONDS);
      } catch (RuntimeException failure) {
        try { releaseOwner(task, owner); } catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
        throw failure;
      }
    }
    JsonNode current() { return owned().value(); }
    void assertOwned() { owned(); }
    private StateDocument owned() {
      if (closed.get() || released.get() || lost.get()) throw new OptimisticConcurrencyException("Schedule lease was lost: " + task.name());
      StateDocument document = state.get(NAMESPACE, key(task.name())).orElseThrow();
      verifyConfiguration(task, document.value());
      Instant expiry = instant(document.value(), "leaseUntil");
      if (!owner.equals(document.value().path("owner").asText()) || expiry == null || !expiry.isAfter(clock.instant())) {
        lost.set(true); throw new OptimisticConcurrencyException("Schedule lease was lost: " + task.name());
      }
      return document;
    }
    void update(UnaryOperator<ObjectNode> mutation) {
      for (int i = 0; i < CAS_ATTEMPTS; i++) {
        StateDocument document = owned();
        try { state.put(NAMESPACE, key(task.name()), document.version(), mutation.apply(object(document.value()))); return; }
        catch (OptimisticConcurrencyException raced) { /* A heartbeat or another worker changed the revision. */ }
      }
      throw new OptimisticConcurrencyException("Could not update schedule: " + task.name());
    }
    @Override public void close() {
      if (!released.compareAndSet(false, true)) return;
      heartbeat.cancel(false);
      releaseOwner(task, owner);
    }
  }
  private void releaseOwner(ScheduledTask task, String owner) {
    for (int i = 0; i < CAS_ATTEMPTS; i++) {
      var document = state.get(NAMESPACE, key(task.name()));
      if (document.isEmpty() || !owner.equals(document.get().value().path("owner").asText())) return;
      ObjectNode value = object(document.get().value()).putNull("owner").putNull("leaseUntil");
      try { state.put(NAMESPACE, key(task.name()), document.get().version(), value); return; }
      catch (OptimisticConcurrencyException raced) { /* Retry only while this owner is still current. */ }
    }
    throw new OptimisticConcurrencyException("Could not release schedule lease: " + task.name());
  }
  private void verifyConfiguration(ScheduledTask task, JsonNode value) {
    if (!configuration(task).equals(value.path("configuration").asText()))
      throw new IllegalStateException("Schedule definition changed; use explicit reconfigure with a new generation: " + task.name());
    String expected = generations.get(task.name());
    if (expected != null && !expected.equals(generation(value)))
      throw new IllegalStateException("Schedule generation changed; restart this worker with the updated configuration: " + task.name());
  }
  private static String configuration(ScheduledTask task) {
    var definition = Json.MAPPER.createObjectNode().put("calendar", task.schedule().descriptor());
    definition.set("input", task.input());
    var identity = Json.MAPPER.createObjectNode();
    identity.put("subject", task.user().subject());
    identity.set("roles", Json.MAPPER.valueToTree(new TreeSet<>(task.user().roles())));
    identity.set("claims", Json.MAPPER.valueToTree(task.user().claims()));
    definition.set("user", identity);
    return hash(canonical(definition).toString());
  }
  private static JsonNode canonical(JsonNode value) {
    if (value.isObject()) {
      var result = Json.MAPPER.createObjectNode();
      var keys = new TreeSet<String>(); value.fieldNames().forEachRemaining(keys::add);
      keys.forEach(key -> result.set(key, canonical(value.get(key)))); return result;
    }
    if (value.isArray()) { var result = Json.MAPPER.createArrayNode(); value.forEach(item -> result.add(canonical(item))); return result; }
    return value.deepCopy();
  }
  private static ObjectNode object(JsonNode value) {
    if (!(value instanceof ObjectNode object)) throw new IllegalStateException("Invalid schedule state");
    return object.deepCopy();
  }
  private ScheduledTask requireTask(String name) {
    ScheduledTask task = tasks.get(name);
    if (task == null) throw new IllegalArgumentException("Unknown schedule: " + name);
    return task;
  }
  private static Instant instant(JsonNode value, String field) { return value.path(field).isTextual() ? Instant.parse(value.get(field).asText()) : null; }
  private static String generation(JsonNode value) { return value.path("generation").isTextual() ? value.get("generation").asText() : "legacy"; }
  private static String receiptId(String name, String generation, Instant scheduledAt) {
    return (generation.equals("legacy") ? "scheduled:" : "__booster_schedule:")
        + hash(name + "\n" + (generation.equals("legacy") ? "" : generation + "\n") + scheduledAt);
  }
  private static String key(String name) { return "schedule:" + hash(name); }
  private static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception impossible) { throw new IllegalStateException(impossible); }
  }
  private static void validateLimit(int value) { if (value < 1 || value > 10000) throw new IllegalArgumentException("Occurrence limit must be 1..10000"); }
  private void checkOpen() { if (closed.get()) throw new IllegalStateException("Scheduler is closed"); }
  private void report(Throwable error) { try { errorHandler.accept(error); } catch (RuntimeException ignored) { /* Reporting does not discard durable work. */ } }
  @Override public void close() {
    if (!closed.compareAndSet(false, true)) return;
    timers.shutdownNow();
    if (timerThreads.contains(Thread.currentThread())) return;
    try {
      if (!timers.awaitTermination(10, TimeUnit.SECONDS))
        throw new IllegalStateException("Scheduled callback did not stop within ten seconds");
    } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
  }
}
