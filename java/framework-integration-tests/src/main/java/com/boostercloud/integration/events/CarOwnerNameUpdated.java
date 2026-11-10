package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A car owner was renamed ({@code src/events/car-owner-name-updated.ts}).
 *
 * @param carOwnerId the owner
 * @param newOwnerName the new name
 */
@Event
public record CarOwnerNameUpdated(UUID carOwnerId, String newOwnerName) {
  /**
   * @return the owner
   */
  public UUID entityID() {
    return carOwnerId;
  }
}
