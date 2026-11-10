package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A product was (soft) deleted ({@code src/events/product-deleted.ts}).
 *
 * @param productId the product
 */
@Event
public record ProductDeleted(UUID productId) {
  /**
   * @return the product
   */
  public UUID entityID() {
    return productId;
  }
}
