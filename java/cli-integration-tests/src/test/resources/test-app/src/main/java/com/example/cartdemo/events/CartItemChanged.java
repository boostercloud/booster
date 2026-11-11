package com.example.cartdemo.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/** The quantity of a product in a cart changed. */
@Event
public record CartItemChanged(UUID cartId, UUID productId, double quantity) {
  public UUID entityID() {
    return cartId;
  }
}
