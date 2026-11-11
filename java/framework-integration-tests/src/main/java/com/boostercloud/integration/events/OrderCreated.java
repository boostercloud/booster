package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.Order;

/**
 * An order was created ({@code src/events/order-created.ts}).
 *
 * @param order the order
 */
@Event
public record OrderCreated(Order order) {
  /**
   * @return the order id
   */
  public UUID entityID() {
    return order.id();
  }
}
