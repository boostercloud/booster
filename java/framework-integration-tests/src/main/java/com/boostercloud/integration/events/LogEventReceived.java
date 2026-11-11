package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A value was logged by the {@code LogEvent} command ({@code src/events/log-event-received.ts}).
 *
 * @param id the entity
 * @param value the logged value
 */
@Event
public record LogEventReceived(UUID id, String value) {
  /**
   * @return the entity
   */
  public UUID entityID() {
    return id;
  }
}
