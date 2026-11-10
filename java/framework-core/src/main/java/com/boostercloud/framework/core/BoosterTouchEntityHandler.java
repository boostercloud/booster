package com.boostercloud.framework.core;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * Port of {@code booster-touch-entity-handler.ts}: emits a {@code BoosterEntityTouched} event for an entity so that
 * its snapshot is regenerated and its read models re-projected (uses the global {@code Booster.config()}).
 *
 * <p>Skeleton: implemented by stage <b>core-2a</b> (spec 06 §3.8).
 */
public final class BoosterTouchEntityHandler {

  private BoosterTouchEntityHandler() {}

  /**
   * {@code touchEntity(entityName, entityId)}. Implemented by stage core-2a.
   *
   * @param entityName the entity class name
   * @param entityId the entity id
   */
  public static void touchEntity(String entityName, UUID entityId) {
    throw new UnsupportedOperationException("not implemented yet: BoosterTouchEntityHandler#touchEntity");
  }
}
