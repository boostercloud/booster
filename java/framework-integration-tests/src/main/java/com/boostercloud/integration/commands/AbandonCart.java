package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.notifications.CartAbandoned;

/**
 * Notifies that a cart was abandoned ({@code src/commands/abandon-cart.ts}): registers the {@link CartAbandoned}
 * notification (not an event), whose partition key is the cart id.
 *
 * @param cartId the cart
 */
@Command(authorize = All.class)
public record AbandonCart(UUID cartId) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(AbandonCart command, Register register) {
    register.events(new CartAbandoned(command.cartId().toString()));
  }
}
