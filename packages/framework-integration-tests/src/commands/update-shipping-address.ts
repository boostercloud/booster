import { Command } from '@boostercloud/framework-core'
import { Address } from '../common/address'
import { UUID, Register } from '@boostercloud/framework-types'
import { ShippingAddressUpdated } from '../events/shipping-address-updated'

@Command({
  authorize: 'all',
})
export class UpdateShippingAddress {
  public constructor(readonly cartId: UUID, readonly address: Address) {}

  public static async handle(command: UpdateShippingAddress, register: Register): Promise<void> {
    register.events(new ShippingAddressUpdated(command.cartId, command.address))
  }
}
