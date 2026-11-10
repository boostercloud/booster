package com.boostercloud.framework.core.coreconcepts.touchentity.events;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * Port of {@code core-concepts/touch-entity/events/booster-entity-touched.ts}: the built-in event emitted by
 * {@code BoosterTouchEntityHandler.touchEntity} so that the snapshot of an entity is rebuilt and its read models are
 * re-projected. Registered by {@code Booster.start} in {@code config.events}; stored with {@code superKind: booster}
 * and {@code entityTypeName = entityName}.
 *
 * @param entityName the touched entity class name
 * @param entityId the touched entity id
 */
public record BoosterEntityTouched(String entityName, UUID entityId) {

  /**
   * @return the id of the entity the event belongs to ({@link #entityId()})
   */
  public UUID entityID() {
    return entityId;
  }
}
