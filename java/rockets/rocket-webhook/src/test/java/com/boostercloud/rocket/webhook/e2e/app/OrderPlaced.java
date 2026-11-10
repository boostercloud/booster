package com.boostercloud.rocket.webhook.e2e.app;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/** Registered by the {@code orders} webhook. */
@Event
public record OrderPlaced(UUID orderId, String product, int quantity, String source) {
  public UUID entityID() {
    return orderId;
  }
}
