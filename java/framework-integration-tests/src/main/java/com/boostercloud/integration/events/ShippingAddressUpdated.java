package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Address;

/**
 * The shipping address of a cart changed ({@code src/events/shipping-address-updated.ts}).
 *
 * @param cartId the cart
 * @param address the new address
 */
@Event
public record ShippingAddressUpdated(UUID cartId, Address address) {
  /**
   * @return the cart
   */
  public UUID entityID() {
    return cartId;
  }
}
