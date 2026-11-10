package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;
import java.util.Map;

/**
 * Port of {@code read-model-schema-migrator.ts}: upgrades a read model from its {@code boosterMetadata.schemaVersion}
 * to the current version, and batch-migrates every stale read model of a type ({@code migrateAll}). Deprecated in
 * TypeScript in favour of data migrations.
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 09 §3.3); trace action
 * {@code READ_MODEL_SCHEMA_MIGRATOR_MIGRATE}.
 */
public class ReadModelSchemaMigrator {

  /** Default page size of {@link #migrateAll(String)}. */
  public static final int LIMIT = 100;

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
   * {@code migrate(readModel, readModelName)}. Implemented by stage core-2b.
   *
   * @param readModel the raw read model (with {@code boosterMetadata})
   * @param readModelName the read model class name
   * @return the same object when no migration is needed, else the migrated read model
   */
  public Map<String, Object> migrate(Map<String, Object> readModel, String readModelName) {
    throw new UnsupportedOperationException("not implemented yet: ReadModelSchemaMigrator#migrate");
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
   * {@code migrateAll(readModelName, batchSize)}. Implemented by stage core-2b.
   *
   * @param readModelName the read model class name
   * @param batchSize the page size
   * @return the number of migrated read models
   */
  public int migrateAll(String readModelName, int batchSize) {
    throw new UnsupportedOperationException("not implemented yet: ReadModelSchemaMigrator#migrateAll");
  }
}
