package com.boostercloud.framework.core.integration.app.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A product changed its name and price.
 *
 * @param productId the product
 * @param displayName the new name
 * @param price the new price
 */
@Event
public record ProductUpdated(UUID productId, String displayName, double price) {
  /**
   * @return the product
   */
  public UUID entityID() {
    return productId;
  }
}
