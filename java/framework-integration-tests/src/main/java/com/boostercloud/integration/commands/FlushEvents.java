package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.Cart;
import com.boostercloud.integration.events.CartItemChanged;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shows {@code register.flush()} ({@code src/commands/flush-events.ts}): registers {@code previousProducts} events,
 * flushes them, reads the cart, registers {@code afterProducts} more (stored only when the handler returns) and reads
 * the cart again. Both carts therefore see only the flushed events.
 *
 * @param cartId the cart
 * @param previousProducts how many products to add before the flush
 * @param afterProducts how many products to add after it
 */
@Command(authorize = All.class)
public record FlushEvents(UUID cartId, double previousProducts, double afterProducts) {

  /**
   * @param command the command
   * @param register the register
   * @return the cart after the flush and the cart after registering the other events
   */
  public static List<Cart> handle(FlushEvents command, Register register) {
    List<CartItemChanged> previousEvents = new ArrayList<>();
    for (int i = 0; i < command.previousProducts(); i++) {
      previousEvents.add(new CartItemChanged(command.cartId(), UUID.generate(), 0));
    }
    List<CartItemChanged> afterEvents = new ArrayList<>();
    for (int i = 0; i < command.afterProducts(); i++) {
      afterEvents.add(new CartItemChanged(command.cartId(), UUID.generate(), 0));
    }

    register.events(previousEvents.toArray());

    register.flush();
    Cart previousCart = getEntity(command.cartId());

    register.events(afterEvents.toArray());
    Cart afterCart = getEntity(command.cartId());
    return Arrays.asList(previousCart, afterCart);
  }

  private static Cart getEntity(UUID cartId) {
    return Booster.entity(Cart.class, cartId).orElse(null);
  }
}
