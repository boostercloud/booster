package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * The quantity of a product in a cart changed ({@code src/events/cart-item-changed.ts}).
 *
 * @param cartId the cart
 * @param productId the product
 * @param quantity the quantity added
 */
@Event
public record CartItemChanged(UUID cartId, UUID productId, double quantity) {
  /**
   * @return the cart
   */
  public UUID entityID() {
    return cartId;
  }

  /**
   * Called by the reducer to check it receives a real instance (TypeScript issue #797).
   *
   * @return the product
   */
  public UUID getProductId() {
    return productId;
  }
}
