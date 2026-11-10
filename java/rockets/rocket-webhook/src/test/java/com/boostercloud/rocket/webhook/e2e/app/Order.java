package com.boostercloud.rocket.webhook.e2e.app;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;

/** An order placed through the webhook and confirmed through GraphQL. */
@Entity
public record Order(UUID id, String product, int quantity, String status, String source) {

  @Reduces(OrderPlaced.class)
  public static Order placed(OrderPlaced event, Order current) {
    return new Order(event.orderId(), event.product(), event.quantity(), "placed", event.source());
  }

  @Reduces(OrderConfirmed.class)
  public static Order confirmed(OrderConfirmed event, Order current) {
    return new Order(current.id(), current.product(), current.quantity(), "confirmed", current.source());
  }
}
