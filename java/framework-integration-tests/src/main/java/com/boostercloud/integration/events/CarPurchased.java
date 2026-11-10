package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.CarModel;
import com.boostercloud.integration.entities.CarOwner;

/**
 * A car was bought ({@code src/events/car-purchased.ts}); it embeds the owner and model snapshots ({@code null} when
 * they did not exist: TypeScript stores {@code undefined} through its non-null assertions).
 *
 * @param id the purchase
 * @param owner the owner
 * @param model the model
 */
@Event
public record CarPurchased(UUID id, CarOwner owner, CarModel model) {
  /**
   * @return the purchase
   */
  public UUID entityID() {
    return id;
  }
}
