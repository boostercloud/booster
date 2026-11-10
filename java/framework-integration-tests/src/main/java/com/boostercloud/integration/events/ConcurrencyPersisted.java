package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A {@code Concurrency} entity was stored ({@code src/events/concurrency-persisted.ts}).
 *
 * @param id the entity
 * @param otherId the other join key
 */
@Event
public record ConcurrencyPersisted(UUID id, UUID otherId) {
  /**
   * @return the entity
   */
  public UUID entityID() {
    return id;
  }
}
