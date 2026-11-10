package com.boostercloud.integration.migrations.events.productupdated;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * Version 2 of the {@code ProductUpdated} event ({@code src/migrations/events/ProductUpdated/schema-versions.ts}):
 * the product and a free-form reason.
 *
 * @param product the updated product
 * @param reason why it changed, as a string
 */
public record ProductUpdatedV2(Product product, String reason) {

  /**
   * @return the product
   */
  public UUID entityID() {
    return product.id();
  }
}
