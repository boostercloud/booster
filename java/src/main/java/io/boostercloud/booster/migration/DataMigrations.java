package io.boostercloud.booster.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.boostercloud.booster.core.AccessPolicy;
import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.core.BoosterException;
import io.boostercloud.booster.core.CommandHandler;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.RequestContext;
import io.boostercloud.booster.core.Register;
import io.boostercloud.booster.core.StateDocument;
import io.boostercloud.booster.core.StateStore;
import io.boostercloud.booster.core.UserContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Ordered, explicitly completed and resumable data migrations.
 *
 * <p>Install the lifecycle events before building Booster, configure a durable StateStore,
 * then construct this engine with the same stable definitions on every worker. A global
 * renewable CAS lease serializes workers. Earlier jobs must finish before later jobs start.
 * run() leaves existing RUNNING/FAILED jobs alone; resume() reruns them from their durable
 * checkpoints. Each step has an immutable idempotency key and atomically commits its
 * events and result through Booster. The journal, rather than a returned callback or a
 * progress document, determines whether a job has finished.
 */
public final class DataMigrations {
  private static final String NAMESPACE = "booster.data-migrations";
  private static final int MAX_CAS_RETRIES = 32;
  private final Booster booster;
  private final StateStore state;
  private final List<DataMigration> migrations;
  private final Map<String, DataMigration> byName;
  private final UserContext user;
  private final Clock clock;
  private final Duration leaseDuration;

  public DataMigrations(Booster booster, List<DataMigration> migrations, UserContext user) {
    this(booster, migrations, user, Clock.systemUTC(), Duration.ofMinutes(1));
  }

  public DataMigrations(Booster booster, List<DataMigration> migrations, UserContext user,
      Clock clock, Duration leaseDuration) {
    this.booster = Objects.requireNonNull(booster, "booster");
    this.state = booster.stateStore();
    this.user = Objects.requireNonNull(user, "Migration execution identity must be explicit");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
    if (leaseDuration.compareTo(Duration.ofSeconds(1)) < 0 || leaseDuration.compareTo(Duration.ofHours(1)) > 0)
      throw new IllegalArgumentException("Migration lease duration must be between one second and one hour");
    var configured = new ArrayList<>(Objects.requireNonNull(migrations));
    configured.sort(Comparator.comparingInt(DataMigration::order).thenComparing(DataMigration::name));
    byName = new LinkedHashMap<>();
    for (var migration : configured) if (byName.putIfAbsent(migration.name(), migration) != null)
      throw new IllegalArgumentException("Duplicate data migration: " + migration.name());
    this.migrations = List.copyOf(configured);
    ensureCatalog();
  }

  public static Booster.Builder install(Booster.Builder builder) {
    return builder.entity(BoosterDataMigrationEntity.class)
        .event(BoosterDataMigrationStarted.class, BoosterDataMigrationStarted::name,
            BoosterDataMigrationEntity.class, BoosterDataMigrationEntity::started)
        .event(BoosterDataMigrationFinished.class, BoosterDataMigrationFinished::name,
            BoosterDataMigrationEntity.class, BoosterDataMigrationEntity::finished)
        .event(BoosterDataMigrationFailed.class, BoosterDataMigrationFailed::name,
            BoosterDataMigrationEntity.class, BoosterDataMigrationEntity::failed);
  }

  /** Return true if any job started or remains unfinished, matching Booster's run indication. */
  public boolean run() { return run(false); }

  /** Retry the first unfinished job, preserving its checkpoint and completed step receipts. */
  public boolean resume() { return run(true); }

  public Optional<MigrationStatus> status(String name) {
    requireMigration(name);
    var lifecycle = booster.entity(BoosterDataMigrationEntity.class, name);
    if (lifecycle == null) return Optional.empty();
    var progress = progress(name);
    return Optional.of(new MigrationStatus(lifecycle, progress.path("checkpoint"),
        progress.path("lastCompletedStep").isTextual() ? progress.get("lastCompletedStep").textValue() : null));
  }

  public List<MigrationStatus> statuses() {
    return migrations.stream().map(migration -> status(migration.name())).flatMap(Optional::stream).toList();
  }

  /**
   * Explicit asynchronous completion, after a start callback returned without finishing.
   * A currently executing worker owns completion until it releases its lease.
   */
  public void finish(String name) {
    requireMigration(name);
    ensureCatalog();
    try (var lease = acquireLease()) {
      var current = booster.entity(BoosterDataMigrationEntity.class, name);
      if (current == null) throw new IllegalStateException("Migration has not started: " + name);
      if (current.status() == DataMigrationStatus.FINISHED) return;
      if (current.status() != DataMigrationStatus.RUNNING)
        throw new IllegalStateException("Resume a failed migration before finishing it");
      booster.executeSystemAction("data-migration-finish", Json.MAPPER.createObjectNode().put("name", name),
          context(name, "finish"), AccessPolicy.publicAccess(), (ignored, register) -> {
            lease.assertOwned(); requireRunning(register, name);
            register.emit(new BoosterDataMigrationFinished(name, now())); return null;
          });
    }
  }

  private boolean run(boolean resume) {
    ensureCatalog();
    if (migrations.isEmpty()) return false;
    boolean started = false;
    try (var lease = acquireLease()) {
      for (var migration : migrations) {
        lease.assertOwned();
        var current = booster.entity(BoosterDataMigrationEntity.class, migration.name());
        if (current != null && current.status() == DataMigrationStatus.FINISHED) continue;
        if (current != null && !resume) return true;
        started = true;
        start(migration, lease);
        var after = booster.entity(BoosterDataMigrationEntity.class, migration.name());
        if (after == null || after.status() != DataMigrationStatus.FINISHED) return true;
      }
      return started;
    }
  }

  private void start(DataMigration migration, Lease lease) {
    String name = migration.name();
    long attempt = mutateProgress(name, value -> value.put("attempt", Math.addExact(value.path("attempt").asLong(0), 1)))
        .path("attempt").longValue();
    var invocation = Json.MAPPER.createObjectNode().put("name", name).put("attempt", attempt);
    invocation.set("parameters", migration.parameters());
    booster.executeSystemAction("data-migration-start", invocation, context(name, "start:" + attempt),
        AccessPolicy.publicAccess(), (ignored, register) -> {
          lease.assertOwned(); register.emit(new BoosterDataMigrationStarted(name, now())); return null;
        });
    try {
      booster.executeSystemAction("data-migration-run", invocation, context(name, "run:" + attempt),
          AccessPolicy.publicAccess(), (ignored, register) -> {
            lease.assertOwned();
            // Read the lifecycle into this transaction's expected stream versions.
            // A newer worker's Started event fences stale buffered effects even if
            // the lease expires after the last ownership check but before commit.
            long lifecycleAttempt = requireRunning(register, name).attempts();
            migration.handler().start(new MigrationContext(this, migration, lease, register));
            lease.assertOwned();
            // A handler may explicitly flush, clearing the Register's previous
            // read set. Re-establish the lifecycle fence without accepting a
            // newer worker's attempt observed after that flush.
            requireAttempt(register, name, lifecycleAttempt, true);
            return null;
          });
    } catch (RuntimeException failure) {
      try {
        lease.assertOwned();
        var current = booster.entity(BoosterDataMigrationEntity.class, name);
        if (current != null && current.status() != DataMigrationStatus.FINISHED) {
          String reason = failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "Migration failed");
          String safeReason = reason.substring(0, Math.min(reason.length(), 2048));
          booster.executeSystemAction("data-migration-failed", invocation, context(name, "failed:" + attempt),
              AccessPolicy.publicAccess(), (ignored, register) -> {
                lease.assertOwned(); register.emit(new BoosterDataMigrationFailed(name, now(), safeReason)); return null;
              });
        }
      } catch (RuntimeException recordingFailure) { failure.addSuppressed(recordingFailure); }
      throw failure;
    }
  }

  JsonNode step(DataMigration migration, Lease lease, String key, JsonNode input, CommandHandler<JsonNode> work) {
    if (key == null || key.isBlank() || key.length() > 512)
      throw new IllegalArgumentException("Migration step keys must contain 1 to 512 characters");
    Objects.requireNonNull(work, "work");
    var envelope = Json.MAPPER.createObjectNode().put("name", migration.name()).put("step", key);
    envelope.set("input", input == null ? NullNode.instance : input.deepCopy());
    envelope.set("parameters", migration.parameters());
    JsonNode result = booster.executeSystemAction("data-migration-step", envelope,
        context(migration.name(), "step:" + hash(key)), AccessPolicy.publicAccess(), (ignored, register) -> {
          lease.assertOwned();
          long lifecycleAttempt = requireRunning(register, migration.name()).attempts();
          Object value = work.handle(envelope.get("input").deepCopy(), register);
          lease.assertOwned(); requireAttempt(register, migration.name(), lifecycleAttempt, false); return value;
        });
    lease.assertOwned();
    checkpoint(migration.name(), null, key);
    return result;
  }

  JsonNode progress(String name) {
    return state.get(NAMESPACE, "job:" + hash(name)).filter(document -> !document.deleted())
        .map(StateDocument::value).orElseGet(() -> Json.MAPPER.createObjectNode()
            .put("attempt", 0).set("checkpoint", Json.MAPPER.createObjectNode()));
  }

  void checkpoint(String name, JsonNode value, String lastStep) {
    mutateProgress(name, previous -> {
      if (value != null) previous.set("checkpoint", value.deepCopy());
      if (lastStep != null) previous.put("lastCompletedStep", lastStep);
      return previous;
    });
  }

  private ObjectNode mutateProgress(String name, UnaryOperator<ObjectNode> mutation) {
    String key = "job:" + hash(name);
    for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
      Optional<StateDocument> document = state.get(NAMESPACE, key);
      ObjectNode current = document.filter(value -> !value.deleted()).map(StateDocument::value)
          .map(value -> (ObjectNode) value).orElseGet(() -> Json.MAPPER.createObjectNode().put("attempt", 0)
              .set("checkpoint", Json.MAPPER.createObjectNode()));
      ObjectNode replacement = mutation.apply(current.deepCopy());
      try { return (ObjectNode) state.put(NAMESPACE, key, document.map(StateDocument::version).orElse(0L), replacement).value(); }
      catch (BoosterException conflict) { if (conflict.code() != BoosterException.Code.CONFLICT) throw conflict; }
    }
    throw conflict("Concurrent migration progress updates did not converge");
  }

  private void ensureCatalog() {
    var configured = Json.MAPPER.createArrayNode();
    for (var migration : migrations) {
      var definition = Json.MAPPER.createObjectNode().put("name", migration.name()).put("order", migration.order());
      definition.set("parameters", migration.parameters()); configured.add(definition);
    }
    for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
      var prior = state.get(NAMESPACE, "catalog");
      if (prior.isPresent() && !prior.get().deleted()) {
        JsonNode catalog = prior.get().value();
        if (!catalog.isArray() || catalog.size() > configured.size()) throw conflict("Previously configured migrations cannot be removed");
        for (int index = 0; index < catalog.size(); index++)
          if (!catalog.get(index).equals(configured.get(index)))
            throw conflict("Persisted migration names, order and parameters are immutable; append a new migration");
        if (catalog.equals(configured)) return;
      }
      try { state.put(NAMESPACE, "catalog", prior.map(StateDocument::version).orElse(0L), configured); return; }
      catch (BoosterException conflict) { if (conflict.code() != BoosterException.Code.CONFLICT) throw conflict; }
    }
    throw conflict("Concurrent migration catalog updates did not converge");
  }

  private DataMigration requireMigration(String name) {
    var definition = byName.get(name);
    if (definition == null) throw new IllegalArgumentException("Unknown data migration: " + name);
    return definition;
  }

  private static BoosterDataMigrationEntity requireRunning(Register register, String name) {
    var current = register.currentEntity(BoosterDataMigrationEntity.class, name);
    if (current == null || current.status() != DataMigrationStatus.RUNNING)
      throw conflict("Migration is not running: " + name);
    return current;
  }

  private static void requireAttempt(Register register, String name, long attempt, boolean allowFinished) {
    var current = register.currentEntity(BoosterDataMigrationEntity.class, name);
    if (current == null || current.attempts() != attempt
        || (current.status() != DataMigrationStatus.RUNNING && !(allowFinished && current.status() == DataMigrationStatus.FINISHED)))
      throw conflict("Migration execution attempt changed: " + name);
  }

  private RequestContext context(String name, String operation) {
    return new RequestContext("__booster_migration:" + hash(name) + ":" + operation, user, Map.of("migration", name));
  }
  Instant now() { return clock.instant(); }

  private Lease acquireLease() {
    String owner = UUID.randomUUID().toString();
    for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
      var current = state.get(NAMESPACE, "lease");
      if (current.isPresent() && !current.get().deleted()
          && current.get().value().path("expiresAt").asLong() > clock.millis())
        throw conflict("Another data-migration worker holds the execution lease");
      var lease = Json.MAPPER.createObjectNode().put("owner", owner)
          .put("expiresAt", Math.addExact(clock.millis(), leaseDuration.toMillis()));
      try {
        StateDocument acquired = state.put(NAMESPACE, "lease", current.map(StateDocument::version).orElse(0L), lease);
        return new Lease(owner, acquired);
      } catch (BoosterException conflict) { if (conflict.code() != BoosterException.Code.CONFLICT) throw conflict; }
    }
    throw conflict("Could not acquire the data-migration execution lease");
  }

  final class Lease implements AutoCloseable {
    private final String owner;
    private StateDocument document;
    private final AtomicReference<RuntimeException> lost = new AtomicReference<>();
    private final ScheduledExecutorService heartbeat;
    private boolean closed;

    Lease(String owner, StateDocument document) {
      this.owner = owner; this.document = document;
      heartbeat = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("booster-migration-lease").factory());
      long interval = Math.max(100, leaseDuration.toMillis() / 3);
      heartbeat.scheduleAtFixedRate(() -> {
        try { renew(); } catch (RuntimeException failure) { lost.compareAndSet(null, failure); }
      }, interval, interval, TimeUnit.MILLISECONDS);
    }

    synchronized void assertOwned() {
      if (closed) throw conflict("Migration execution lease has closed");
      if (lost.get() != null) throw conflict("Migration execution lease was lost", lost.get());
      var current = state.get(NAMESPACE, "lease");
      if (current.isEmpty() || current.get().deleted() || !current.get().value().path("owner").asText().equals(owner)
          || current.get().value().path("expiresAt").asLong() <= clock.millis())
        throw conflict("Migration execution lease expired or was replaced");
      document = current.get();
    }

    private synchronized void renew() {
      if (closed || lost.get() != null) return;
      assertOwned();
      var value = document.value().deepCopy();
      ((ObjectNode) value).put("expiresAt", Math.addExact(clock.millis(), leaseDuration.toMillis()));
      document = state.put(NAMESPACE, "lease", document.version(), value);
    }

    @Override public synchronized void close() {
      if (closed) return;
      closed = true; heartbeat.shutdownNow();
      var current = state.get(NAMESPACE, "lease");
      if (current.isPresent() && !current.get().deleted() && current.get().value().path("owner").asText().equals(owner)) {
        try { state.delete(NAMESPACE, "lease", current.get().version()); }
        catch (BoosterException conflict) { if (conflict.code() != BoosterException.Code.CONFLICT) throw conflict; }
      }
    }
  }

  private static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
  }
  private static BoosterException conflict(String message) { return new BoosterException(BoosterException.Code.CONFLICT, message); }
  private static BoosterException conflict(String message, Throwable cause) { return new BoosterException(BoosterException.Code.CONFLICT, message, cause); }
}
