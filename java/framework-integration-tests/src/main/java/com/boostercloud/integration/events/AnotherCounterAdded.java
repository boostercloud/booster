package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * An {@code AnotherCounter} was incremented ({@code src/events/another-counter-added.ts}).
 *
 * @param anotherCounterId the counter
 * @param identifier its identifier
 */
@Event
public record AnotherCounterAdded(UUID anotherCounterId, String identifier) {
  /**
   * @return the counter
   */
  public UUID entityID() {
    return anotherCounterId;
  }
}
