import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { Address } from '../common/address'

@Event
export class UpdatedShippingAddress {
  public constructor(readonly cartId: UUID, readonly address: Address) {}

  public entityID(): string {
    return this.cartId
  }
}
