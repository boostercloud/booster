package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.AnotherCounterAdded;

/**
 * A second counter ({@code src/entities/another-counter.ts}).
 *
 * @param id the counter
 * @param identifier its identifier
 * @param amount how many times it was incremented
 */
@Entity(authorizeReadEvents = All.class)
public record AnotherCounter(UUID id, String identifier, double amount) {

  /**
   * @return the counter id (called to check reducers receive real instances)
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @param currentAnotherCounter the current counter, or {@code null}
   * @return the counter incremented by one
   */
  @Reduces(AnotherCounterAdded.class)
  public static AnotherCounter anotherCounterAdded(AnotherCounterAdded event, AnotherCounter currentAnotherCounter) {
    if (currentAnotherCounter != null) {
      return new AnotherCounter(currentAnotherCounter.id(), currentAnotherCounter.identifier(),
          currentAnotherCounter.amount() + 1);
    }
    return new AnotherCounter(event.anotherCounterId(), event.identifier(), 1);
  }
}
