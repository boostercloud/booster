package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A {@code Counter} was incremented ({@code src/events/counter-added.ts}).
 *
 * @param counterId the counter
 * @param identifier its identifier
 */
@Event
public record CounterAdded(UUID counterId, String identifier) {
  /**
   * @return the counter
   */
  public UUID entityID() {
    return counterId;
  }
}
