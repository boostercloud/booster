package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CartPaid;
import com.boostercloud.integration.roles.UserWithPhone;

/**
 * The payment of a cart ({@code src/entities/payment.ts}); projected into {@code CartReadModel} by {@code cartId}.
 * Only users with the {@code UserWithPhone} role can read its events.
 *
 * @param id the payment
 * @param cartId the paid cart
 * @param confirmationToken the payment confirmation
 */
@Entity(authorizeReadEvents = UserWithPhone.class)
public record Payment(UUID id, UUID cartId, String confirmationToken) {

  /**
   * @param event the event
   * @param currentPayment the current payment (ignored)
   * @return the payment
   */
  @Reduces(CartPaid.class)
  public static Payment payCart(CartPaid event, Payment currentPayment) {
    return new Payment(event.paymentId(), event.cartId(), event.confirmationToken());
  }
}
