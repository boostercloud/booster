package com.example.store.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.store.common.Address;
import com.example.store.common.CartItem;
import com.example.store.events.CartItemChanged;
import java.util.List;
import java.util.Optional;

@Entity(authorizeReadEvents = All.class)
public record Cart(UUID id, List<CartItem> cartItems, Optional<Address> shippingAddress, int checks) {
  @Reduces(CartItemChanged.class)
  public static Cart changeItem(CartItemChanged event, Cart currentCart) {
    return new Cart(event.cartId(), List.of(new CartItem(event.productId(), event.quantity())), Optional.empty(), 0);
  }
}
