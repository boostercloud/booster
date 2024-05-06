import { Booster, NonExposed, Query } from '@boostercloud/framework-core'
import { QueryInfo, QueryInput, UserEnvelope, UUID } from '@boostercloud/framework-types'
import { Cart } from '../entities/cart'
import {
  beforeHookQueryID,
  beforeHookQueryMultiply,
  queryHandlerErrorCartId,
  queryHandlerErrorCartMessage,
} from '../constants'

@Query({
  authorize: 'all',
  before: [CartTotalQuantity.beforeFn],
})
export class CartTotalQuantity {
  public constructor(readonly cartId: UUID, @NonExposed readonly multiply: number) {}

  public static async beforeFn(input: QueryInput, currentUser?: UserEnvelope): Promise<QueryInput> {
    if (input.cartId === beforeHookQueryID) {
      input.multiply = beforeHookQueryMultiply
      return input
    }
    input.multiply = 1
    return input
  }

  public static async handle(query: CartTotalQuantity, queryInfo: QueryInfo): Promise<number> {
    if (query.cartId === queryHandlerErrorCartId) {
      throw new Error(queryHandlerErrorCartMessage)
    }
    const cart = await Booster.entity(Cart, query.cartId)
    if (!cart || !cart.cartItems || cart.cartItems.length === 0) {
      return 0
    }
    return cart?.cartItems
      .map((cartItem) => cartItem.quantity)
      .reduce((accumulator, value) => {
        return accumulator + value * query.multiply
      }, 0)
  }
}
