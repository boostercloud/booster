import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CartChecked {
  public constructor(readonly cartId: UUID) {}

  public entityID(): UUID {
    return this.cartId
  }
}
