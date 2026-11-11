package com.boostercloud.rocket.webhook.e2e.app;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;

/** A regular command acting on an entity the webhook created. */
@Command(authorize = All.class)
public record ConfirmOrder(UUID orderId) {
  public static void handle(ConfirmOrder command, Register register) {
    register.events(new OrderConfirmed(command.orderId()));
  }
}
