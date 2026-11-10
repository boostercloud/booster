package com.boostercloud.framework.core.integration.app.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A product was created (schema version 2: version 1, {@link ProductCreatedV1}, had no category).
 *
 * @param productId the product
 * @param sku the stock keeping unit
 * @param displayName the name shown to customers
 * @param price the price
 * @param category the category
 */
@Event
public record ProductCreated(UUID productId, String sku, String displayName, double price, String category) {
  /**
   * @return the product
   */
  public UUID entityID() {
    return productId;
  }
}
