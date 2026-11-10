package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A car owner was added ({@code src/events/car-owner-added.ts}).
 *
 * @param id the owner
 * @param name the owner's name
 */
@Event
public record CarOwnerAdded(UUID id, String name) {
  /**
   * @return the owner
   */
  public UUID entityID() {
    return id;
  }
}
