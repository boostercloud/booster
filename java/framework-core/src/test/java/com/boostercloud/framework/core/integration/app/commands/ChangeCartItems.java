package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.integration.app.common.CartItem;
import com.boostercloud.framework.core.integration.app.events.CartItemChanged;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.List;

/**
 * Sets several lines of a cart at once; returns a summary object.
 *
 * @param cartId the cart
 * @param items the new lines
 */
@Command(authorize = All.class)
public record ChangeCartItems(UUID cartId, List<CartItem> items) {

  /**
   * What the mutation returns.
   *
   * @param cartId the cart
   * @param lines how many lines changed
   * @param items the lines
   */
  public record ChangeSummary(UUID cartId, int lines, List<CartItem> items) {}

  /**
   * @param command the command
   * @param register the register
   * @return the summary
   */
  public static ChangeSummary handle(ChangeCartItems command, Register register) {
    for (CartItem item : command.items()) {
      register.events(new CartItemChanged(command.cartId(), item.productId(), item.quantity()));
    }
    return new ChangeSummary(command.cartId(), command.items().size(), command.items());
  }
}
