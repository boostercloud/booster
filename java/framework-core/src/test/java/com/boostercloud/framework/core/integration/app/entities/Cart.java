package com.boostercloud.framework.core.integration.app.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.core.integration.app.common.CartItem;
import com.boostercloud.framework.core.integration.app.events.CartChecked;
import com.boostercloud.framework.core.integration.app.events.CartItemChanged;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * A shopping cart.
 *
 * @param id the cart
 * @param items its lines, in insertion order
 * @param checks how many times the cart was checked
 */
@Entity(authorizeReadEvents = All.class)
public record Cart(UUID id, List<CartItem> items, int checks) {

  /**
   * @param event the change
   * @param current the cart, or {@code null}
   * @return the new cart
   */
  @Reduces(CartItemChanged.class)
  public static Cart changeItem(CartItemChanged event, Cart current) {
    Cart cart = current == null ? new Cart(event.cartId(), List.of(), 0) : current;
    List<CartItem> items = new ArrayList<>();
    boolean found = false;
    for (CartItem item : cart.items()) {
      if (item.productId().equals(event.productId())) {
        found = true;
        if (event.quantity() > 0) {
          items.add(new CartItem(item.productId(), event.quantity()));
        }
      } else {
        items.add(item);
      }
    }
    if (!found && event.quantity() > 0) {
      items.add(new CartItem(event.productId(), event.quantity()));
    }
    return new Cart(cart.id(), items, cart.checks());
  }

  /**
   * @param event the check
   * @param current the cart, or {@code null}
   * @return the new cart
   */
  @Reduces(CartChecked.class)
  public static Cart checked(CartChecked event, Cart current) {
    Cart cart = current == null ? new Cart(event.cartId(), List.of(), 0) : current;
    return new Cart(cart.id(), cart.items(), cart.checks() + 1);
  }
}
