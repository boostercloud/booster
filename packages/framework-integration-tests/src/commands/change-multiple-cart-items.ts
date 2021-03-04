import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/cart-item-changed'

@Command({
  authorize: 'all',
})
export class ChangeMultipleCartItems {
  public constructor(readonly cartId: UUID, readonly itemsCount: number) {}

  public static async handle(command: ChangeMultipleCartItems, register: Register): Promise<void> {
    const quantity = 1
    const events = [...Array(command.itemsCount).keys()].map(
      (id) => new CartItemChanged(command.cartId, id.toString(), quantity)
    )
    register.events(...events)
  }
}
