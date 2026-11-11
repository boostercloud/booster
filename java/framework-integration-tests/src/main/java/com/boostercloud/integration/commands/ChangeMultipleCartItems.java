package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CartItemChanged;
import java.util.stream.IntStream;

/**
 * Adds one unit of the products {@code "0"} to {@code itemsCount - 1} to a cart, all in one
 * {@code register.events} call ({@code src/commands/change-multiple-cart-items.ts}).
 *
 * @param cartId the cart
 * @param itemsCount how many products
 */
@Command(authorize = All.class)
public record ChangeMultipleCartItems(UUID cartId, double itemsCount) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(ChangeMultipleCartItems command, Register register) {
    double quantity = 1;
    Object[] events = IntStream.range(0, (int) command.itemsCount())
        .mapToObj(id -> new CartItemChanged(command.cartId(), UUID.of(String.valueOf(id)), quantity))
        .toArray();
    register.events(events);
  }
}
