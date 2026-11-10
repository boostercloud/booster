package com.boostercloud.framework.core;

import com.boostercloud.framework.core.coreconcepts.touchentity.events.BoosterEntityTouched;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.LinkedHashMap;

/**
 * Port of {@code booster-touch-entity-handler.ts}: stores a {@link BoosterEntityTouched} event for an entity so that,
 * once it is dispatched, the entity snapshot is rebuilt (same state, newer {@code snapshottedEventCreatedAt}) and its
 * read models are re-projected; handlers of {@code BoosterEntityTouched} and the global event handlers receive it too.
 *
 * <p>Uses the global {@link Booster#config()}, a new random request id and no user. Nothing is validated: touching
 * an entity that has no state stores an event that reduces to nothing.
 */
public final class BoosterTouchEntityHandler {

  private BoosterTouchEntityHandler() {}

  /**
   * {@code touchEntity(entityName, entityId)}: stores the event ({@code superKind: booster},
   * {@code entityTypeName = entityName}, {@code entityID = entityId}) with retry/backoff.
   *
   * @param entityName the entity class name
   * @param entityId the entity id
   */
  public static void touchEntity(String entityName, UUID entityId) {
    UUID requestID = UUID.generate();
    Register register = new Register(requestID, new LinkedHashMap<>(), RegisterHandler::flush);
    register.events(new BoosterEntityTouched(entityName, entityId));
    RegisterHandler.handle(Booster.config(), register);
  }
}
