import { Register, UUID } from '@boostercloud/framework-types'
import { Booster, Command } from '@boostercloud/framework-core'
import { CartReadModel } from '../read-models/cart-read-model'

@Command({
  authorize: 'all',
})
export class CartMyAddress {
  public constructor(readonly cartId: UUID, readonly paginatedVersion: boolean) {}

  public static async handle(command: CartMyAddress, register: Register): Promise<unknown> {
    return (await Booster.readModel(CartReadModel)
      .filter({
        id: {
          eq: command.cartId,
        },
      })
      .select(['id', 'myAddress'])
      .paginatedVersion(command.paginatedVersion)
      .search()) as Array<unknown>
  }
}
