package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.errors.InvalidVersionError;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Port of {@code read-model-schema-migrator.ts}: upgrades a read model from its {@code boosterMetadata.schemaVersion}
 * (1 when absent) to the current version ({@code config.currentVersionFor(readModelName)}), and batch-migrates every
 * stale read model of a type ({@code migrateAll}). Deprecated in TypeScript in favour of data migrations.
 *
 * <p>Read models are handled as raw documents ({@code Map<String, Object>} with the {@code boosterMetadata} key), the
 * form in which they cross the provider interface:
 *
 * <ul>
 *   <li>a schema version lower than 1: {@link InvalidVersionError} {@code Received an invalid schema version value,
 *       <v>, for <readModel>. Versions must be greater than 0};</li>
 *   <li>a version newer than the current one: {@link InvalidVersionError} {@code Can not migrate schema an unknown
 *       version: The current schema version of <readModel> is <current>, which is lower than the received version
 *       <v>};</li>
 *   <li>the current version: the <b>same</b> map is returned;</li>
 *   <li>an older version: each step binds the value to {@code fromSchema} ({@code Instances.createInstance}), creates
 *       a new migration class instance and calls the {@code @ToVersion} method; the result of the last step, as a new
 *       raw map, gets the original {@code boosterMetadata} with {@code schemaVersion = current}.</li>
 * </ul>
 *
 * <p>{@link #migrate} is traced as {@code READ_MODEL_SCHEMA_MIGRATOR_MIGRATE}.
 */
public class ReadModelSchemaMigrator {

  /** Default page size of {@link #migrateAll(String)}. */
  public static final int LIMIT = 100;

  private static final String BOOSTER_METADATA = "boosterMetadata";

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public ReadModelSchemaMigrator(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code migrate(readModel, readModelName)}.
   *
   * <p><b>Note:</b> read model schema migration is deprecated. Prefer data migrations.
   *
   * @param readModel the raw read model (with {@code boosterMetadata})
   * @param readModelName the read model class name
   * @return the same object when no migration is needed, else the migrated read model
   */
  public Map<String, Object> migrate(Map<String, Object> readModel, String readModelName) {
    return Tracer.trace(TraceActionTypes.READ_MODEL_SCHEMA_MIGRATOR_MIGRATE.value(), ReadModelSchemaMigrator.class,
        "migrate", Arrays.asList(readModel, readModelName), () -> {
          checkVersionRange(readModel, readModelName);
          if (needsMigration(readModel, readModelName)) {
            return applyAllMigrations(readModel, readModelName);
          }
          return readModel; // The current version is exactly the same as the version of the concept
        });
  }

  /**
   * {@code migrateAll(readModelName)} with batches of {@link #LIMIT}.
   *
   * @param readModelName the read model class name
   * @return the number of migrated read models
   */
  public int migrateAll(String readModelName) {
    return migrateAll(readModelName, LIMIT);
  }

  /**
   * {@code migrateAll(readModelName, batchSize)}: pages through the read models whose
   * {@code boosterMetadata.schemaVersion} is lower than the current version or absent
   * ({@code { or: [ { boosterMetadata: { schemaVersion: { lt: current } } }, { boosterMetadata: { schemaVersion: {
   * isDefined: false } } } ] }}, paginated search with {@code limit = batchSize}); every page is migrated and then
   * stored concurrently (fail fast), each read model with {@code version + 1} and the previous version as the expected
   * one. A page shorter than {@code batchSize} ends the loop even when the provider returns a cursor. No version range
   * check is made here (as in TypeScript).
   *
   * @param readModelName the read model class name
   * @param batchSize the page size
   * @return the number of read models fetched (and migrated)
   */
  public int migrateAll(String readModelName, int batchSize) {
    Map<String, Object> filterFor = buildFilterForSearchReadModelsToMigrate(readModelName);
    Object cursor = null;
    int total = 0;
    do {
      ReadModelListResult<Map<String, Object>> toMigrate =
          searchReadModelsToMigrate(readModelName, filterFor, batchSize, cursor);
      List<Map<String, Object>> items = toMigrate.items();
      cursor = items.size() >= batchSize ? toMigrate.cursor() : null;
      List<Callable<Map<String, Object>>> migrations = new ArrayList<>();
      for (Map<String, Object> item : items) {
        migrations.add(() -> applyAllMigrations(item, readModelName));
      }
      List<Map<String, Object>> migratedReadModels = Promises.runAll(migrations);
      List<Callable<Void>> persists = new ArrayList<>();
      for (Map<String, Object> readModel : migratedReadModels) {
        persists.add(() -> {
          persistReadModel(readModel, readModelName);
          return null;
        });
      }
      Promises.runAll(persists);
      total += items.size();
    } while (Js.isTruthy(cursor));
    return total;
  }

  private void checkVersionRange(Map<String, Object> readModel, String readModelName) {
    int readModelVersion = readModelSchemaVersion(readModel);
    if (readModelVersion < 1) {
      throw new InvalidVersionError("Received an invalid schema version value, " + readModelVersion + ", for "
          + readModelName + ". Versions must be greater than 0");
    }
    int currentVersion = config.currentVersionFor(readModelName);
    if (currentVersion < readModelVersion) {
      throw new InvalidVersionError("Can not migrate schema an unknown version: The current schema version of "
          + readModelName + " is " + currentVersion + ", which is lower than the received version " + readModelVersion);
    }
  }

  private boolean needsMigration(Map<String, Object> readModel, String readModelName) {
    return config.currentVersionFor(readModelName) > readModelSchemaVersion(readModel);
  }

  private Map<String, Object> applyAllMigrations(Map<String, Object> oldReadModel, String readModelName) {
    Logger logger = Loggers.getLogger(config, "ReadModelSchemaMigrator#applyAllMigrations");
    int oldVersion = readModelSchemaVersion(oldReadModel);
    int currentVersion = config.currentVersionFor(readModelName);
    logger.info("Migrating Schema " + readModelName + " from version " + oldVersion + " to version " + currentVersion);
    logger.debug("ReadModel before schema migration:\n", oldReadModel);

    Map<Integer, SchemaMigrationMetadata> migrations = config.schemaMigrations.get(readModelName);
    Object migratedValue = oldReadModel;
    for (int toVersion = oldVersion + 1; toVersion <= currentVersion; toVersion++) {
      migratedValue = applyMigration(migratedValue, migrations == null ? null : migrations.get(toVersion));
    }

    Map<String, Object> newReadModel = toMutableRawMap(migratedValue);
    Map<String, Object> boosterMetadata = new LinkedHashMap<>(boosterMetadataOf(oldReadModel));
    boosterMetadata.put("schemaVersion", currentVersion);
    newReadModel.put(BOOSTER_METADATA, boosterMetadata);

    logger.debug("ReadModel after schema migration:\n", newReadModel);
    return newReadModel;
  }

  private Object applyMigration(Object oldValue, SchemaMigrationMetadata migration) {
    Logger logger = Loggers.getLogger(config, "ReadModelSchemaMigrator#applyMigration");
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
    logger.debug("Partial schema migration finished. Schema migrated from oldValue=" + json(oldConcept) + " to newValue="
        + json(newConcept));
    return newConcept;
  }

  /** {@code readModel.boosterMetadata?.schemaVersion ?? 1}. */
  static int readModelSchemaVersion(Map<String, Object> readModel) {
    Object schemaVersion = boosterMetadataOf(readModel).get("schemaVersion");
    return switch (schemaVersion) {
      case null -> 1;
      case Number number -> number.intValue();
      case String text -> parseVersion(text);
      default -> 1;
    };
  }

  private static int parseVersion(String text) {
    try {
      return (int) Double.parseDouble(text.trim());
    } catch (NumberFormatException e) {
      return 1;
    }
  }

  /** The {@code boosterMetadata} of a raw read model as a raw map (empty when absent). */
  @SuppressWarnings("unchecked")
  static Map<String, Object> boosterMetadataOf(Map<String, Object> readModel) {
    Object metadata = readModel == null ? null : readModel.get(BOOSTER_METADATA);
    return switch (metadata) {
      case null -> Map.of();
      case Map<?, ?> map -> (Map<String, Object>) map;
      default -> BoosterJson.toRawMap(metadata);
    };
  }

  private Map<String, Object> buildFilterForSearchReadModelsToMigrate(String readModelName) {
    int expectedVersion = config.currentVersionFor(readModelName);
    Map<String, Object> lowerVersion = Map.of(BOOSTER_METADATA, Map.of("schemaVersion", Map.of("lt", expectedVersion)));
    Map<String, Object> noVersion = Map.of(BOOSTER_METADATA, Map.of("schemaVersion", Map.of("isDefined", false)));
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put("or", List.of(lowerVersion, noVersion));
    return filter;
  }

  private ReadModelListResult<Map<String, Object>> searchReadModelsToMigrate(String readModelName,
      Map<String, Object> filterFor, int limit, Object cursor) {
    ReadModelListResult<Map<String, Object>> result = config.provider().readModels()
        .search(config, readModelName, filterFor, new LinkedHashMap<>(), limit, cursor, true, null);
    if (result == null) {
      throw new IllegalStateException("The provider returned no result when searching the read models " + readModelName
          + " to migrate");
    }
    return result;
  }

  private void persistReadModel(Map<String, Object> newReadModel, String readModelName) {
    Logger logger = Loggers.getLogger(config, "ReadModelSchemaMigrator#persistReadModel");
    if (newReadModel == null || newReadModel.get(BOOSTER_METADATA) == null) {
      throw new IllegalStateException("Error migrating ReadModel: " + (newReadModel == null ? "undefined" : Js.toString(newReadModel)));
    }
    Map<String, Object> previousMetadata = boosterMetadataOf(newReadModel);
    long currentReadModelVersion = previousMetadata.get("version") instanceof Number version ? version.longValue() : 0;
    Object schemaVersion = previousMetadata.get("schemaVersion") != null
        ? previousMetadata.get("schemaVersion")
        : config.currentVersionFor(readModelName);
    Map<String, Object> boosterMetadata = new LinkedHashMap<>(previousMetadata);
    boosterMetadata.put("version", currentReadModelVersion + 1);
    boosterMetadata.put("schemaVersion", schemaVersion);
    newReadModel.put(BOOSTER_METADATA, boosterMetadata);
    logger.debug("Storing new version of read model", newReadModel);
    config.provider().readModels().store(config, readModelName, newReadModel, currentReadModelVersion);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toMutableRawMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return new LinkedHashMap<>((Map<String, Object>) map);
    }
    return new LinkedHashMap<>(BoosterJson.toRawMap(value));
  }

  private static String json(Object value) {
    try {
      return BoosterJson.toJson(value);
    } catch (RuntimeException e) {
      return String.valueOf(value);
    }
  }
}
