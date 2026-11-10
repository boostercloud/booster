package com.example.store.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

@Event
public record CartItemChanged(UUID cartId, UUID productId, double quantity) {
  public UUID entityID() { return cartId; }
}
