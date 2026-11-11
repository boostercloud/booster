package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.List;

/**
 * A car offer was added ({@code src/events/car-offer-added.ts}).
 *
 * @param id the offer
 * @param name its name
 * @param purchasesIds the purchases it applies to
 */
@Event
public record CarOfferAdded(UUID id, String name, List<String> purchasesIds) {
  /**
   * @return the offer
   */
  public UUID entityID() {
    return id;
  }
}
