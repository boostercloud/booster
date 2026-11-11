package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.errors.InvalidVersionError;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

/**
 * Port of {@code schema-migrator.ts}: lazily upgrades a command, event or entity-snapshot envelope from its stored
 * schema version to the current one ({@code config.currentVersionFor(typeName)}) by chaining the {@code @ToVersion}
 * methods, one version at a time. Traced as {@code SCHEMA_MIGRATOR_MIGRATE}.
 *
 * <ul>
 *   <li>{@code version < 1}: {@link InvalidVersionError} {@code Received an invalid schema version value, <v>, for
 *       <type>. Versions must be greater than 0};</li>
 *   <li>a version newer than the current one: {@link InvalidVersionError} {@code Can not migrate schema an unknown
 *       version: The current schema version of <type> is <current>, which is lower than the received version
 *       <v>};</li>
 *   <li>the current version: the <b>same</b> envelope is returned;</li>
 *   <li>an older version: a new envelope with the migrated value and {@code version = current}. Each step binds the
 *       value to {@code fromSchema} ({@code Instances.createInstance}), creates a new migration class instance and
 *       calls the method; the result of the last step is the new value (for commands, whose value is the raw input
 *       map, it is converted back to a raw map).</li>
 * </ul>
 */
public class SchemaMigrator {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public SchemaMigrator(BoosterConfig config) {
    this.config = config;
  }

  /**
   * Migrates a command envelope.
   *
   * @param conceptEnvelope the envelope
   * @return the same envelope or a migrated copy
   */
  public CommandEnvelope migrate(CommandEnvelope conceptEnvelope) {
    return traced(conceptEnvelope, envelope -> migrate(envelope, envelope.typeName(), envelope.version(), envelope.value(),
        (value, version) -> envelope.toBuilder().value(toRawMap(value)).version(version).build()));
  }

  /**
   * Migrates an event envelope.
   *
   * @param conceptEnvelope the envelope
   * @return the same envelope or a migrated copy
   */
  public EventEnvelope migrate(EventEnvelope conceptEnvelope) {
    return traced(conceptEnvelope, envelope -> migrate(envelope, envelope.typeName(), envelope.version(), envelope.value(),
        (value, version) -> envelope.toBuilder().value(value).version(version).build()));
  }

  /**
   * Migrates an entity snapshot envelope.
   *
   * @param conceptEnvelope the envelope
   * @return the same envelope or a migrated copy
   */
  public EntitySnapshotEnvelope migrate(EntitySnapshotEnvelope conceptEnvelope) {
    return traced(conceptEnvelope, envelope -> migrate(envelope, envelope.typeName(), envelope.version(), envelope.value(),
        (value, version) -> envelope.toBuilder().value(value).version(version).build()));
  }

  private <E> E traced(E envelope, Function<E, E> migration) {
    return Tracer.trace(TraceActionTypes.SCHEMA_MIGRATOR_MIGRATE.value(), SchemaMigrator.class, "migrate",
        Arrays.asList(envelope), () -> migration.apply(envelope));
  }

  private <E> E migrate(E envelope, String typeName, int version, Object value, Rebuilder<E> rebuilder) {
    checkVersionRange(typeName, version);
    if (needsMigration(typeName, version)) {
      return applyAllMigrations(envelope, typeName, version, value, rebuilder);
    }
    return envelope;
  }

  private void checkVersionRange(String typeName, int version) {
    if (version < 1) {
      throw new InvalidVersionError("Received an invalid schema version value, " + version + ", for " + typeName
          + ". Versions must be greater than 0");
    }
    int currentVersion = config.currentVersionFor(typeName);
    if (currentVersion < version) {
      throw new InvalidVersionError("Can not migrate schema an unknown version: The current schema version of " + typeName
          + " is " + currentVersion + ", which is lower than the received version " + version);
    }
  }

  private boolean needsMigration(String typeName, int version) {
    return config.currentVersionFor(typeName) > version;
  }

  private <E> E applyAllMigrations(E oldEnvelope, String typeName, int oldVersion, Object value, Rebuilder<E> rebuilder) {
    Logger logger = Loggers.getLogger(config, "SchemaMigrator#applyAllMigrations");
    int currentVersion = config.currentVersionFor(typeName);
    logger.info("Migrating schema " + typeName + " from version " + oldVersion + " to version " + currentVersion);
    logger.debug("Envelope before schema migration:\n", oldEnvelope);
    Map<Integer, SchemaMigrationMetadata> migrations = config.schemaMigrations.get(typeName);
    Object migratedValue = value;
    for (int toVersion = oldVersion + 1; toVersion <= currentVersion; toVersion++) {
      migratedValue = applyMigration(migratedValue, migrations.get(toVersion));
    }
    E newEnvelope = rebuilder.rebuild(migratedValue, currentVersion);
    logger.debug("Envelope after migration:\n", newEnvelope);
    return newEnvelope;
  }

  private Object applyMigration(Object oldValue, SchemaMigrationMetadata migration) {
    Logger logger = Loggers.getLogger(config, "SchemaMigrator#applyMigration");
    if (migration == null) {
      throw new InvalidVersionError(
          "Received an undefined schema migration value. Are there \"gaps\" between the versions of the schema migrations?");
    }
    Object oldConcept = Instances.createInstance(migration.fromSchema(), oldValue);
    Object migrationInstance = UserCode.newInstance(migration.migrationClass());
    Method migrationMethod = UserCode.findInstance(migration.migrationClass(), migration.methodName(), oldConcept)
        .orElseThrow(() -> new IllegalStateException("The schema migration " + migration.migrationClass().getSimpleName()
            + "." + migration.methodName() + " cannot receive a " + migration.fromSchema().getSimpleName()));
    Object newConcept = UserCode.invoke(migrationMethod, migrationInstance, oldConcept);
    logger.debug("Partial migration finished. Migrated from oldValue=" + oldConcept + " to newValue=" + newConcept);
    return newConcept;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toRawMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : BoosterJson.toRawMap(value);
  }

  @FunctionalInterface
  private interface Rebuilder<E> {
    E rebuild(Object value, int version);
  }
}
