package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A cart was checked ({@code src/events/cart-checked.ts}).
 *
 * @param cartId the cart
 */
@Event
public record CartChecked(UUID cartId) {
  /**
   * @return the cart
   */
  public UUID entityID() {
    return cartId;
  }
}
