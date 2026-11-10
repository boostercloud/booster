package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.ConcurrencyPersisted;

/**
 * An entity projected twice into the same read model ({@code src/entities/concurrency.ts}).
 *
 * @param id the entity
 * @param otherId the second join key
 */
@Entity(authorizeReadEvents = All.class)
public record Concurrency(UUID id, UUID otherId) {

  /**
   * @return the entity id
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @param currentConcurrency the current entity (ignored)
   * @return the entity
   */
  @Reduces(ConcurrencyPersisted.class)
  public static Concurrency persisted(ConcurrencyPersisted event, Concurrency currentConcurrency) {
    return new Concurrency(event.id(), event.otherId());
  }
}
