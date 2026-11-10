package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.Cart;
import com.boostercloud.integration.notifications.CartAbandoned;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link FlushEvents} with {@link CartAbandoned} notifications ({@code src/commands/flush-notifications.ts}). The cart
 * only changes asynchronously (notification, {@code HandleCartAbandoned}, {@code CartChecked}), so it is polled: up
 * to 11 attempts, 0, 1, ..., 10 seconds apart, then {@code Could not get the cart}.
 *
 * @param cartId the cart
 * @param previousProducts how many notifications to register before the flush
 * @param afterProducts how many notifications to register after it
 */
@Command(authorize = All.class)
public record FlushNotifications(UUID cartId, double previousProducts, double afterProducts) {

  /**
   * @param command the command
   * @param register the register
   * @return the cart after the flush and the cart after registering the other notifications
   */
  public static List<Cart> handle(FlushNotifications command, Register register) {
    List<CartAbandoned> previousEvents = new ArrayList<>();
    for (int i = 0; i < command.previousProducts(); i++) {
      previousEvents.add(new CartAbandoned(command.cartId().toString()));
    }
    List<CartAbandoned> afterEvents = new ArrayList<>();
    for (int i = 0; i < command.afterProducts(); i++) {
      afterEvents.add(new CartAbandoned(command.cartId().toString()));
    }

    System.out.println("REGISTERING PREVIOUS EVENTS " + previousEvents);
    register.events(previousEvents.toArray());

    register.flush();
    System.out.println("GETTING CART");
    Cart previousCart = getCart(command.cartId());

    System.out.println("REGISTERING AFTER EVENTS " + afterEvents);
    register.events(afterEvents.toArray());

    System.out.println("GETTING SECOND CART");
    Cart afterCart = getCart(command.cartId());
    return Arrays.asList(previousCart, afterCart);
  }

  /** The TypeScript {@code do { setTimeout(...) } while (!cart && maxRetries-- > 0)} loop. */
  private static Cart getCart(UUID cartId) {
    int maxRetries = 10;
    Cart cart;
    do {
      sleep(1000L * (10 - maxRetries));
      cart = Booster.entity(Cart.class, cartId).orElse(null);
    } while (cart == null && maxRetries-- > 0);
    if (cart == null) {
      throw new IllegalStateException("Could not get the cart");
    }
    return cart;
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the cart", e);
    }
  }
}
