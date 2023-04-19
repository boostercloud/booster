import { Booster, Query } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { Cart } from '../entities/cart'
import { queryHandlerErrorCartId, queryHandlerErrorCartMessage, queryHandlerErrorIgnoredCartId } from '../constants'

@Query({
  authorize: 'all',
})
export class CartTotalQuantity {
  public constructor(readonly cartId: UUID) {}

  public static async handle(query: CartTotalQuantity): Promise<number> {
    if (query.cartId === queryHandlerErrorCartId || query.cartId === queryHandlerErrorIgnoredCartId) {
      throw new Error(queryHandlerErrorCartMessage)
    }
    const cart = await Booster.entity(Cart, query.cartId)
    if (!cart || !cart.cartItems || cart.cartItems.length === 0) {
      return 0
    }
    return cart?.cartItems
      .map((cartItem) => cartItem.quantity)
      .reduce((accumulator, value) => {
        return accumulator + value
      }, 0)
  }
}
