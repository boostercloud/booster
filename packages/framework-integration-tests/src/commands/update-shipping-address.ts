import { Command } from '@boostercloud/framework-core'
import { Address } from '../common/address'
import { UUID, Register } from '@boostercloud/framework-types'
import { UpdatedShippingAddress } from '../events/updated-shipping-address'

@Command({
  authorize: 'all',
})
export class UpdateShippingAddress {
  public constructor(readonly cartId: UUID, readonly address: Address) {}

  public handle(register: Register): void {
    register.events(new UpdatedShippingAddress(this.cartId, this.address))
  }
}
