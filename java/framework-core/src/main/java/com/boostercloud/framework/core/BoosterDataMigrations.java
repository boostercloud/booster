package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.coreconcepts.datamigration.entities.BoosterDataMigrationEntity;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationStarted;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterEntityMigrated;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.core.services.EventStore;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.DataMigrationInterface;
import com.boostercloud.framework.types.concepts.DataMigrationMetadata;
import com.boostercloud.framework.types.concepts.DataMigrationStatus;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port of {@code booster-data-migrations.ts}: runs the {@code @DataMigration} classes in {@code order}, tracking their
 * status through the built-in {@code BoosterDataMigrationEntity}, and emits {@code BoosterEntityMigrated} events.
 * Uses the global {@code Booster.config()}.
 *
 * <p>{@link #run()} (traced as {@code MIGRATION_RUN}) goes through the migrations sorted by {@code order} (stable: equal
 * orders keep the registration order), one at a time. For each it loads the {@code BoosterDataMigrationEntity} whose
 * id is the migration class name:
 *
 * <ul>
 *   <li>none: the migration has never started; {@code BoosterDataMigrationStarted(name, now)} is stored right away
 *       (its own register and request id), then the static {@code start(register)} of the class runs with another
 *       register whose events are stored when it returns;</li>
 *   <li>{@code RUNNING}: it is still running (or never finished); it is not started again and does not block the
 *       next migrations;</li>
 *   <li>any other status: finished, skipped.</li>
 * </ul>
 *
 * The framework never emits {@code BoosterDataMigrationFinished}: the migration registers it itself
 * ({@code register.events(new BoosterDataMigrationFinished("<MigrationClassName>"))}). An exception thrown by
 * {@code start} propagates and stops the run (the status stays {@code RUNNING}).
 */
public final class BoosterDataMigrations {

  private BoosterDataMigrations() {}

  /**
   * {@code run()}.
   *
   * @return {@code true} when a migration was started or is still running, {@code false} when every migration has
   *     finished (or none is defined)
   */
  public static boolean run() {
    return Tracer.trace(TraceActionTypes.MIGRATION_RUN.value(), BoosterDataMigrations.class, "run", new ArrayList<>(),
        BoosterDataMigrations::runMigrations);
  }

  private static boolean runMigrations() {
    BoosterConfig config = Booster.config();
    Logger logger = Loggers.getLogger(config, "BoosterDataMigrationDispatcher#dispatch");
    boolean migrating = false;

    Map<String, DataMigrationMetadata> configuredMigrations = config.dataMigrationHandlers;
    if (configuredMigrations.isEmpty()) {
      logger.debug("No defined migrations found. Skipping...");
      return false;
    }

    List<DataMigrationMetadata> sortedConfiguredMigrations = sortConfiguredMigrations(configuredMigrations);
    EventStore eventStore = new EventStore(config);
    for (DataMigrationMetadata configuredMigration : sortedConfiguredMigrations) {
      Optional<EntitySnapshotEnvelope> migrationEntityForConfiguredMigration = eventStore.fetchEntitySnapshot(
          BoosterDataMigrationEntity.class.getSimpleName(), UUID.of(configuredMigration.type().getSimpleName()));
      if (migrationEntityForConfiguredMigration == null || migrationEntityForConfiguredMigration.isEmpty()) {
        logger.debug("Not found running or finished migrations for the DataMigration", configuredMigration);
        migrating = true;
        migrate(configuredMigration);
      } else if (statusOf(migrationEntityForConfiguredMigration.get().value()) == DataMigrationStatus.RUNNING) {
        logger.debug("Found running migrations for the DataMigration", configuredMigration);
        migrating = true;
      }
    }
    return migrating;
  }

  /**
   * {@code migrateEntity(oldEntityName, oldEntityId, newEntity)}: stores one {@code BoosterEntityMigrated} event (its
   * own register with a random request id, no user) in the stream of the old entity; reducing it replaces that
   * entity's snapshot with {@code newEntity}, whose class name is the new entity name.
   *
   * @param oldEntityName the migrated entity class name
   * @param oldEntityId the migrated entity id
   * @param newEntity the new entity instance
   */
  public static void migrateEntity(String oldEntityName, UUID oldEntityId, Object newEntity) {
    UUID requestID = UUID.generate();
    Register register = new Register(requestID, new LinkedHashMap<>(), RegisterHandler::flush);
    register.events(new BoosterEntityMigrated(oldEntityName, oldEntityId, newEntity.getClass().getSimpleName(), newEntity));
    RegisterHandler.handle(Booster.config(), register);
  }

  /** Ascending {@code order}, stable. */
  static List<DataMigrationMetadata> sortConfiguredMigrations(Map<String, DataMigrationMetadata> configuredMigrations) {
    List<DataMigrationMetadata> sorted = new ArrayList<>(configuredMigrations.values());
    sorted.sort(Comparator.comparingInt(migration -> migration.migrationOptions().order()));
    return sorted;
  }

  private static void migrate(DataMigrationMetadata migrationHandler) {
    Register startedRegister = new Register(UUID.generate(), new LinkedHashMap<>(), RegisterHandler::flush);

    emitStarted(startedRegister, migrationHandler.type().getSimpleName());
    RegisterHandler.handle(Booster.config(), startedRegister);

    Register finishedRegister = new Register(UUID.generate(), new LinkedHashMap<>(), RegisterHandler::flush);
    UserCode.invokeStatic(migrationHandler.type(), DataMigrationInterface.START_METHOD, finishedRegister);
    RegisterHandler.handle(Booster.config(), finishedRegister);
  }

  private static void emitStarted(Register register, String configuredMigrationName) {
    Logger logger = Loggers.getLogger(Booster.config(), "BoosterMigration#emitStarted");
    logger.info("Migration started", configuredMigrationName);
    register.events(new BoosterDataMigrationStarted(configuredMigrationName, Dates.nowIso()));
  }

  /** The status of a {@code BoosterDataMigrationEntity} snapshot value (an instance or a raw document). */
  private static DataMigrationStatus statusOf(Object value) {
    Object status = switch (value) {
      case null -> null;
      case BoosterDataMigrationEntity entity -> entity.status();
      case Map<?, ?> map -> map.get("status");
      default -> BoosterJson.toRawMap(value).get("status");
    };
    if (status == null) {
      return null;
    }
    if (status instanceof DataMigrationStatus dataMigrationStatus) {
      return dataMigrationStatus;
    }
    try {
      return DataMigrationStatus.fromValue(status);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
