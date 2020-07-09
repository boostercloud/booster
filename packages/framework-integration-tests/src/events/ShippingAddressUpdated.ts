import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { Address } from '../common/address'

@Event
export class ShippingAddressUpdated {
  public constructor(readonly cartId: UUID, readonly address: Address) {}

  public entityID(): UUID {
    return this.cartId
  }
}
