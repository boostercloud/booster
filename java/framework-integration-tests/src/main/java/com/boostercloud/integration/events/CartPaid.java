package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A cart was paid ({@code src/events/cart-paid.ts}).
 *
 * @param paymentId the payment
 * @param cartId the cart
 * @param confirmationToken the payment confirmation
 */
@Event
public record CartPaid(UUID paymentId, UUID cartId, String confirmationToken) {
  /**
   * @return the payment
   */
  public UUID entityID() {
    return paymentId;
  }
}
