package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;

/**
 * Deletes one stored event ({@code src/commands/hard-delete.ts}); the handler takes no register, as in TypeScript.
 *
 * @param entityId the entity of the event
 * @param entityTypeName the entity type
 * @param createdAt the event's {@code createdAt}
 */
@Command(authorize = All.class)
public record HardDelete(String entityId, String entityTypeName, String createdAt) {

  /**
   * @param command the command
   * @return whether an event was deleted
   */
  public static boolean handle(HardDelete command) {
    EventDeleteParameters parameters =
        new EventDeleteParameters(command.entityTypeName(), UUID.of(command.entityId()), command.createdAt());
    return Booster.deleteEvent(parameters);
  }
}
