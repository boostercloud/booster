package com.boostercloud.framework.core.integration.app.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * The quantity of a product in a cart changed ({@code 0} removes it).
 *
 * @param cartId the cart
 * @param productId the product
 * @param quantity the new quantity
 */
@Event
public record CartItemChanged(UUID cartId, UUID productId, int quantity) {
  /**
   * @return the cart
   */
  public UUID entityID() {
    return cartId;
  }
}
