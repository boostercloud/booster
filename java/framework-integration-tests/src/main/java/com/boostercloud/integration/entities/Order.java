package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Address;
import com.boostercloud.integration.events.OrderCreated;
import java.util.List;

/**
 * An order ({@code src/entities/order.ts}). Its events cannot be read through the API (no
 * {@code authorizeReadEvents}, which denies by default).
 *
 * @param id the order
 * @param shippingAddress where to ship it
 * @param orderItems its lines
 * @param fromCart the cart it comes from
 */
@Entity
public record Order(UUID id, Address shippingAddress, List<OrderItem> orderItems, Cart fromCart) {

  /**
   * @param event the event
   * @return the order carried by the event
   */
  @Reduces(OrderCreated.class)
  public static Order createOrder(OrderCreated event) {
    return event.order();
  }
}
