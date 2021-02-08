import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CartItemChanged {
  public constructor(readonly cartId: UUID, readonly productId: UUID, readonly quantity: number) {}

  public entityID(): UUID {
    return this.cartId
  }
}
