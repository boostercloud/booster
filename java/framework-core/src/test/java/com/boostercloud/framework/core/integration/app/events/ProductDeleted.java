package com.boostercloud.framework.core.integration.app.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A product was withdrawn.
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
