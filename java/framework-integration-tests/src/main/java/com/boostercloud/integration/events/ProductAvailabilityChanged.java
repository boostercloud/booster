package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * The availability of a product changed ({@code src/events/product-availability-changed.ts}).
 *
 * @param productID the product
 * @param quantity the change (negative when units left)
 */
@Event
public record ProductAvailabilityChanged(UUID productID, double quantity) {
  /**
   * @return the product
   */
  public UUID entityID() {
    return productID;
  }
}
