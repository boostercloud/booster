import { Command } from '@boostercloud/framework-core'
import { Address } from '../common/address'
import { UUID, Register } from '@boostercloud/framework-types'
import { ShippingAddressUpdated } from '../events/ShippingAddressUpdated'

@Command({
  authorize: 'all',
})
export class UpdateShippingAddress {
  public constructor(readonly cartId: UUID, readonly address: Address) {}

  public async handle(register: Register): Promise<void> {
    register.events(new ShippingAddressUpdated(this.cartId, this.address))
  }
}
