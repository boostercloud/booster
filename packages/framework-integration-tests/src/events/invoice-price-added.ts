import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class InvoicePriceAdded {
  public constructor(readonly id: UUID, readonly totalPrice: number) {}

  public entityID(): UUID {
    return this.id
  }
}
