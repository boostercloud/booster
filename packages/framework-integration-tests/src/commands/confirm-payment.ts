import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CartPaid } from '../events/cart-paid'

/**
 * Most payment platforms like PayPal have callbacks to confirm payment.
 * Ideally, we should set this command URL as the payment method callback URL
 */
@Command({
  authorize: 'all',
})
export class ConfirmPayment {
  public constructor(readonly cartId: UUID, readonly confirmationToken: string) {}

  public handle(register: Register): void {
    register.events(new CartPaid(this.cartId, this.confirmationToken))
  }
}
