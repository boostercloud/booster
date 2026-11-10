package com.boostercloud.integration.scheduledcommands;

import com.boostercloud.framework.core.decorators.ScheduledCommand;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CartChecked;

/**
 * Checks the cart {@code the-checked-cart} every minute ({@code src/scheduled-commands/check-cart-count.ts}).
 */
@ScheduledCommand(minute = "0/1")
public class CheckCartCount {

  /**
   * @param register the register
   */
  public static void handle(Register register) {
    register.events(new CartChecked(UUID.of("the-checked-cart")));
  }
}
