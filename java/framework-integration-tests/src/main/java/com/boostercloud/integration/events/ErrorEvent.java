package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Something failed ({@code src/events/error-event.ts}). No entity reduces it, so registering it fails (see
 * {@code MoveStock}), as in TypeScript.
 *
 * @param id the id of the failure
 * @param message what failed
 * @param payload the related data
 */
@Event
public record ErrorEvent(UUID id, String message, Object payload) {
  /**
   * @return the id
   */
  public UUID entityID() {
    return id;
  }
}
