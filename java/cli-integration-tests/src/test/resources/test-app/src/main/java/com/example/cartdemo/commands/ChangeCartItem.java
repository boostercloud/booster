package com.example.cartdemo.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.cartdemo.events.CartItemChanged;

/** Sets the quantity of a product in a cart. */
@Command(authorize = All.class)
public record ChangeCartItem(UUID cartId, UUID productId, double quantity) {
  public static void handle(ChangeCartItem command, Register register) {
    register.events(new CartItemChanged(command.cartId(), command.productId(), command.quantity()));
  }
}
