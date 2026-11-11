package com.boostercloud.integration.migrations.events.productupdated;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * Version 1 of the {@code ProductUpdated} event ({@code src/migrations/events/ProductUpdated/schema-versions.ts}):
 * only the product.
 *
 * @param product the updated product
 */
public record ProductUpdatedV1(Product product) {

  /**
   * @return the product
   */
  public UUID entityID() {
    return product.id();
  }
}
