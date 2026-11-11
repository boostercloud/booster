package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.List;

/**
 * A pack of products was created ({@code src/events/pack-created.ts}).
 *
 * @param packID the pack
 * @param name its name
 * @param products the products in it
 */
@Event
public record PackCreated(UUID packID, String name, List<UUID> products) {
  /**
   * @return the pack
   */
  public UUID entityID() {
    return packID;
  }
}
