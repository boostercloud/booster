package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A car model was renamed ({@code src/events/car-model-name-updated.ts}).
 *
 * @param carModelId the model
 * @param newModelName its new name
 */
@Event
public record CarModelNameUpdated(UUID carModelId, String newModelName) {
  /**
   * @return the model
   */
  public UUID entityID() {
    return carModelId;
  }
}
