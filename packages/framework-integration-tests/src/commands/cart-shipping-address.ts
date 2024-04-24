import { Booster, Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CartReadModel } from '../read-models/cart-read-model'

@Command({
  authorize: 'all',
})
export class CartShippingAddress {
  public constructor(readonly cartId: UUID, readonly paginatedVersion: boolean) {}

  public static async handle(command: CartShippingAddress, register: Register): Promise<unknown> {
    return (await Booster.readModel(CartReadModel)
      .filter({
        id: {
          eq: command.cartId,
        },
      })
      .select(['id', 'shippingAddress'])
      .paginatedVersion(command.paginatedVersion)
      .search()) as Array<unknown>
  }
}
