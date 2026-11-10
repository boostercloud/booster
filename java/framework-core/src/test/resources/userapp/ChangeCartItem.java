package com.example.store.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.decorators.NonExposed;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.store.common.ValidateInput;
import com.example.store.events.CartItemChanged;

@Command(authorize = All.class, before = {ValidateInput.class})
public record ChangeCartItem(UUID cartId, UUID productId, double quantity, @NonExposed Double test) {
  public static void handle(ChangeCartItem command, Register register) {
    register.events(new CartItemChanged(command.cartId(), command.productId(), command.quantity()));
  }
}
