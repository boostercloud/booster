import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class ProductAvailabilityChanged {
  public constructor(readonly productID: UUID, readonly quantity: number) {}

  public entityID(): UUID {
    return this.productID
  }
}
