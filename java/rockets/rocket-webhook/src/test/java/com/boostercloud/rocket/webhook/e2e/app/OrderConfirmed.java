package com.boostercloud.rocket.webhook.e2e.app;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/** Registered by the {@link ConfirmOrder} command. */
@Event
public record OrderConfirmed(UUID orderId) {
  public UUID entityID() {
    return orderId;
  }
}
