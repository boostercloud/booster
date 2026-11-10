package com.boostercloud.framework.core.coreconcepts.datamigration.events;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * Port of {@code core-concepts/data-migration/events/booster-entity-migrated.ts}: emitted by
 * {@code BoosterDataMigrations.migrateEntity}; reducing it replaces the snapshot of the old entity with
 * {@code newEntity}. Stored with {@code superKind: booster} and {@code entityTypeName = oldEntityName}.
 *
 * @param oldEntityName the class name of the migrated entity
 * @param oldEntityId the id of the migrated entity
 * @param newEntityName the class name of the new entity
 * @param newEntity the new entity (serialised inline)
 */
public record BoosterEntityMigrated(String oldEntityName, UUID oldEntityId, String newEntityName, Object newEntity) {

  /**
   * @return the id of the entity the event belongs to ({@link #oldEntityId()})
   */
  public UUID entityID() {
    return oldEntityId;
  }
}
