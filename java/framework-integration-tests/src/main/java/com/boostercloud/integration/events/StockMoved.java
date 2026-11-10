package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Units of a product moved between warehouses ({@code src/events/stock-moved.ts}); {@code provider} and
 * {@code customer} are the outside world.
 *
 * @param productID the product (the {@code Stock} id)
 * @param origin where the units come from
 * @param destination where they go
 * @param quantity how many
 */
@Event
public record StockMoved(String productID, String origin, String destination, double quantity) {
  /**
   * @return the product
   */
  public UUID entityID() {
    return UUID.of(productID);
  }

  /**
   * Called by the event handler to check it receives a real instance (TypeScript issue #797).
   *
   * @return the origin
   */
  public String getOrigin() {
    return origin;
  }
}
