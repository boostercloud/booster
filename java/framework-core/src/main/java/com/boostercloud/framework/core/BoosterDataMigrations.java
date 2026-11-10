package com.boostercloud.framework.core;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * Port of {@code booster-data-migrations.ts}: runs the {@code @DataMigration} classes in {@code order}, tracking their
 * status through the built-in {@code BoosterDataMigrationEntity}, and emits {@code BoosterEntityMigrated} events.
 * Uses the global {@code Booster.config()}.
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 09 §3.5); trace action {@code MIGRATION_RUN}.
 */
public final class BoosterDataMigrations {

  private BoosterDataMigrations() {}

  /**
   * {@code run()}. Implemented by stage core-2b.
   *
   * @return whether a migration was started or is still running
   */
  public static boolean run() {
    throw new UnsupportedOperationException("not implemented yet: BoosterDataMigrations#run");
  }

  /**
   * {@code migrateEntity(oldEntityName, oldEntityId, newEntity)}. Implemented by stage core-2b.
   *
   * @param oldEntityName the migrated entity class name
   * @param oldEntityId the migrated entity id
   * @param newEntity the new entity instance
   */
  public static void migrateEntity(String oldEntityName, UUID oldEntityId, Object newEntity) {
    throw new UnsupportedOperationException("not implemented yet: BoosterDataMigrations#migrateEntity");
  }
}
