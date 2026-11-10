package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.LogEventReceivedTest;

/**
 * The entity of {@link LogEventReceivedTest} ({@code src/entities/log-event-test.ts}).
 *
 * @param id the entity
 * @param value the last value
 */
@Entity(authorizeReadEvents = All.class)
public record LogEventTest(UUID id, String value) {

  /**
   * @return the entity id
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @param current the current entity (ignored)
   * @return the entity
   */
  @Reduces(LogEventReceivedTest.class)
  public static LogEventTest eventReceived(LogEventReceivedTest event, LogEventTest current) {
    return new LogEventTest(event.entityID(), event.value());
  }
}
