import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/CartItemChanged'

@Command({
  authorize: 'all',
})
export class ChangeCartItem {
  public constructor(readonly cartId: UUID, readonly productId: UUID, readonly quantity: number) {}

  public async handle(register: Register): Promise<void> {
    register.events(new CartItemChanged(this.cartId, this.productId, this.quantity))
  }
}
