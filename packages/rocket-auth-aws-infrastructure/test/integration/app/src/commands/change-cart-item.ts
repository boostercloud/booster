import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/cart-item-changed'

@Command({
  authorize: 'all',
})
export class ChangeCartItem {
  public constructor(readonly cartId: UUID, readonly productId: UUID, readonly quantity: number) {}

  public static async handle(command: ChangeCartItem, register: Register): Promise<void> {
    register.events(new CartItemChanged(command.cartId, command.productId, command.quantity))
  }
}
