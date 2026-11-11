package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.integration.app.events.CartItemChanged;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Sets the quantity of a product in a cart; anybody can do it.
 *
 * @param cartId the cart
 * @param productId the product
 * @param quantity the quantity ({@code 0} removes the product)
 */
@Command(authorize = All.class, before = RejectNegativeQuantity.class)
public record ChangeCartItem(UUID cartId, UUID productId, int quantity) {
  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(ChangeCartItem command, Register register) {
    register.events(new CartItemChanged(command.cartId(), command.productId(), command.quantity()));
  }
}
