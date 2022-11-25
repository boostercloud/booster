import { Booster, Query } from '@boostercloud/framework-core'
import { CartReadModel } from '../read-models/cart-read-model'

@Query({
  authorize: 'all',
})
export class CartsByCountry {
  public constructor() {}

  public static async handle(query: CartsByCountry): Promise<Record<string, Array<CartReadModel>>> {
    const carts: Array<CartReadModel> = (await Booster.readModel(CartReadModel)
      .filter({
        shippingAddress: {
          country: {
            isDefined: true,
          },
        },
      })
      .paginatedVersion(false)
      .search()) as Array<CartReadModel>

    return carts.reduce((group: Record<string, Array<CartReadModel>>, cartReadModel: CartReadModel) => {
      const country = cartReadModel.shippingAddress?.country || ''
      group[country] = group[country] ?? []
      group[country].push(cartReadModel)
      return group
    }, {})
  }
}
