package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CartPaid;

/**
 * Pays a cart ({@code src/commands/confirm-payment.ts}); the {@code Payment} entity is projected into the cart's
 * {@code CartReadModel} by {@code cartId}.
 *
 * @param paymentId the payment
 * @param cartId the paid cart
 * @param confirmationToken the payment provider's token
 */
@Command(authorize = All.class)
public record ConfirmPayment(UUID paymentId, UUID cartId, String confirmationToken) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(ConfirmPayment command, Register register) {
    register.events(new CartPaid(command.paymentId(), command.cartId(), command.confirmationToken()));
  }
}
