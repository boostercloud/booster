package io.boostercloud.booster.migration;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.CommandHandler;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.Register;
import java.util.Objects;

/**
 * Resumable migration work. Use stable step keys for committed effects, then checkpoint.
 * Arbitrary network calls in start are at-least-once and require the remote service's
 * idempotency key; event receipts cannot make a remote side effect exactly-once.
 */
public final class MigrationContext {
  private final DataMigrations engine;
  private final DataMigration migration;
  private final DataMigrations.Lease lease;
  private final Register register;
  private final long attempt;
  private boolean finished;

  MigrationContext(DataMigrations engine, DataMigration migration, DataMigrations.Lease lease, Register register, long attempt) {
    this.engine = engine; this.migration = migration; this.lease = lease; this.register = register; this.attempt = attempt;
  }
  public String name() { return migration.name(); }
  public JsonNode parameters() { return migration.parameters(); }
  public JsonNode checkpoint() { lease.assertOwned(); return engine.progress(migration.name()).path("checkpoint").deepCopy(); }
  public Register register() { lease.assertOwned(); return register; }

  /** Persist progress only after its corresponding durable work has succeeded. */
  public void checkpoint(JsonNode value) {
    lease.assertOwned(); engine.checkpoint(migration.name(), lease, attempt, Objects.requireNonNull(value), null);
  }

  /** Events and the step result commit atomically; repeats with identical input replay the result. */
  public JsonNode step(String key, JsonNode input, CommandHandler<JsonNode> work) {
    lease.assertOwned();
    if (finished) throw new IllegalStateException("A finished migration cannot perform another step");
    return engine.step(migration, lease, attempt, key, input, work);
  }

  public void migrateEntity(String stepKey, String priorEntityName, String priorId, Object replacement) {
    Objects.requireNonNull(replacement);
    var input = Json.MAPPER.createObjectNode().put("oldEntityName", priorEntityName).put("oldEntityId", priorId)
        .put("newEntityName", replacement.getClass().getSimpleName());
    input.set("replacement", Json.MAPPER.valueToTree(replacement));
    step(stepKey, input, (ignored, work) -> { work.migrateEntity(priorEntityName, priorId, replacement); return null; });
  }

  public void touch(String stepKey, Class<?> entityType, String id) {
    step(stepKey, Json.MAPPER.createObjectNode().put("entityType", entityType.getSimpleName()).put("id", id),
        (ignored, work) -> { work.touch(entityType, id); return null; });
  }

  /** Explicit completion is buffered in the handler's Register and committed on success. */
  public void finish() {
    lease.assertOwned();
    if (!finished) { register.emit(new BoosterDataMigrationFinished(name(), engine.now())); finished = true; }
  }
}
