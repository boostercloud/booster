import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { ChangedCartItem } from '../events/changed-cart-item'

@Command({
  authorize: 'all',
})
export class ChangeCartItem {
  public constructor(readonly cartId: UUID, readonly productId: UUID, readonly quantity: number) {}

  public handle(register: Register): void {
    register.events(new ChangedCartItem(this.cartId, this.productId, this.quantity))
  }
}
