package com.boostercloud.framework.core.integration.app.handlers;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.ScheduledCommand;
import com.boostercloud.framework.core.integration.app.notifications.CartAbandoned;
import com.boostercloud.framework.core.integration.app.readmodels.CartReadModel;
import com.boostercloud.framework.types.concepts.Register;

/** Every five minutes, every empty cart is notified as abandoned. */
@ScheduledCommand(minute = "0/5")
public class FlagEmptyCarts {
  /**
   * @param register the register
   */
  public static void handle(Register register) {
    for (CartReadModel cart : Booster.readModel(CartReadModel.class).search()) {
      if (cart.totalQuantity() == 0) {
        register.events(new CartAbandoned(cart.id()));
      }
    }
  }
}
