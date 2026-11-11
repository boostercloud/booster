package com.boostercloud.framework.core.fixtures.app.domain;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.List;

/**
 * An entity of the fixture application.
 *
 * @param id the id
 * @param items the items
 */
@Entity
public record Cart(UUID id, List<Item> items) {
  /**
   * A nested type (imported right after its enclosing class).
   *
   * @param sku the product
   */
  public record Item(String sku) {}

  /**
   * @param event the event
   * @param current the current cart
   * @return the new cart
   */
  @Reduces(CartCreated.class)
  public static Cart created(CartCreated event, Cart current) {
    return new Cart(event.cartId(), List.of());
  }
}
