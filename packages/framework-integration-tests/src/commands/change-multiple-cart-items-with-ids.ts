import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/cart-item-changed'

interface Items {
  cartId: UUID
  itemsCount: number
}

@Command({
  authorize: 'all',
})
export class ChangeMultipleCartItemsWithIds {
  public constructor(readonly items: Array<Items>) {}

  public static async handle(command: ChangeMultipleCartItemsWithIds, register: Register): Promise<void> {
    const quantity = 1
    const events = command.items.flatMap((item) => {
      return [...Array(item.itemsCount).keys()].map((id) => new CartItemChanged(item.cartId, id.toString(), quantity))
    })
    register.events(...events)
  }
}
