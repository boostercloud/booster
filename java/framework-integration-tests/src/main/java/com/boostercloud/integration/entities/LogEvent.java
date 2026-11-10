package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.LogEventReceived;

/**
 * The entity of {@link LogEventReceived} ({@code src/entities/log-event.ts}). It shares its simple name with the
 * command {@code commands.LogEvent}, as in TypeScript: Booster keeps entities and commands in separate registries.
 *
 * @param id the entity
 * @param value the last value
 */
@Entity(authorizeReadEvents = All.class)
public record LogEvent(UUID id, String value) {

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
  @Reduces(LogEventReceived.class)
  public static LogEvent eventReceived(LogEventReceived event, LogEvent current) {
    return new LogEvent(event.entityID(), event.value());
  }
}
