package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CounterAdded;

/**
 * A counter ({@code src/entities/counter.ts}).
 *
 * @param id the counter
 * @param identifier its identifier
 * @param amount how many times it was incremented
 */
@Entity(authorizeReadEvents = All.class)
public record Counter(UUID id, String identifier, double amount) {

  /**
   * @return the counter id (called to check reducers receive real instances)
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @param currentCounter the current counter, or {@code null}
   * @return the counter incremented by one
   */
  @Reduces(CounterAdded.class)
  public static Counter counterAdded(CounterAdded event, Counter currentCounter) {
    if (currentCounter != null) {
      return new Counter(currentCounter.id(), currentCounter.identifier(), currentCounter.amount() + 1);
    }
    return new Counter(event.counterId(), event.identifier(), 1);
  }
}
