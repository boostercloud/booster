package com.example.cartdemo.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.cartdemo.common.CartItem;
import com.example.cartdemo.events.CartItemChanged;
import java.util.ArrayList;
import java.util.List;

/** A cart is a temporary object where users accumulate all the items they want to buy. */
@Entity(authorizeReadEvents = All.class)
public record Cart(UUID id, List<CartItem> cartItems) {
  @Reduces(CartItemChanged.class)
  public static Cart changeItem(CartItemChanged event, Cart currentCart) {
    List<CartItem> items = new ArrayList<>(currentCart == null ? List.of() : currentCart.cartItems());
    items.removeIf(item -> item.productId().equals(event.productId()));
    items.add(new CartItem(event.productId(), event.quantity()));
    return new Cart(event.cartId(), items);
  }
}
