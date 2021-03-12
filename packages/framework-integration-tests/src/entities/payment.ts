import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CartPaid } from '../events/cart-paid'
import { UserWithPhone } from '../roles'

@Entity({
  authorizeReadEvents: [UserWithPhone],
})
export class Payment {
  public constructor(readonly id: UUID, readonly cartId: UUID, readonly confirmationToken: string) {}

  @Reduces(CartPaid)
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  public static payCart(event: CartPaid, _currentPayment: Payment): Payment {
    return new Payment(event.paymentId, event.cartId, event.confirmationToken)
  }
}
