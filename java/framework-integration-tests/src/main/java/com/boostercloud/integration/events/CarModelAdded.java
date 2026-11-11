package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A car model was added ({@code src/events/car-model-added.ts}).
 *
 * @param id the model
 * @param name its name
 * @param brand its brand
 */
@Event
public record CarModelAdded(UUID id, String name, String brand) {
  /**
   * @return the model
   */
  public UUID entityID() {
    return id;
  }
}
