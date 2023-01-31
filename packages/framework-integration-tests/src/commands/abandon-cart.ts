import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CartAbandoned } from '../notifications/cart-abandoned'

/**
 * Most payment platforms like PayPal have callbacks to confirm payment.
 * Ideally, we should set this command URL as the payment method callback URL
 */
@Command({
  authorize: 'all',
})
export class AbandonCart {
  public constructor(readonly cartId: UUID) {}

  public static async handle(command: AbandonCart, register: Register): Promise<void> {
    register.notifications(new CartAbandoned('a'))
  }
}
