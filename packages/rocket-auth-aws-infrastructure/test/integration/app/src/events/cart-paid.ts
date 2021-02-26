import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CartPaid {
  public constructor(readonly paymentId: UUID, readonly cartId: UUID, readonly confirmationToken: string) {}

  public entityID(): UUID {
    return this.paymentId
  }
}
