package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Registered by the global event handler for every {@link LogEventReceived}
 * ({@code src/events/log-event-received-test.ts}).
 *
 * @param id the entity
 * @param value the logged value
 */
@Event
public record LogEventReceivedTest(UUID id, String value) {
  /**
   * @return the entity
   */
  public UUID entityID() {
    return id;
  }
}
