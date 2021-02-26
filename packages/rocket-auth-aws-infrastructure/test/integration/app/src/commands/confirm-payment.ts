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
  public constructor(readonly paymentId: UUID, readonly cartId: UUID, readonly confirmationToken: string) {}

  public static async handle(command: ConfirmPayment, register: Register): Promise<void> {
    register.events(new CartPaid(command.paymentId, command.cartId, command.confirmationToken))
  }
}
