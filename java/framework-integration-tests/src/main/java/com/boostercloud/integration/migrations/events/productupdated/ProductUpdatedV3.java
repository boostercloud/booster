package com.boostercloud.integration.migrations.events.productupdated;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.ProductUpdateReason;

/**
 * Version 3 of the {@code ProductUpdated} event ({@code src/migrations/events/ProductUpdated/schema-versions.ts}):
 * the product and a {@link ProductUpdateReason}.
 *
 * @param product the updated product
 * @param reason why it changed
 */
public record ProductUpdatedV3(Product product, ProductUpdateReason reason) {

  /**
   * @return the product
   */
  public UUID entityID() {
    return product.id();
  }
}
