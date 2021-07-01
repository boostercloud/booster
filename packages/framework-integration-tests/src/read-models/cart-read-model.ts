import { Projects, ReadModel } from '@boostercloud/framework-core'
import { FilterFor, ProjectionResult, UserEnvelope, UUID } from '@boostercloud/framework-types'
import { CartItem } from '../common/cart-item'
import { Address } from '../common/address'
import { Cart } from '../entities/cart'
import { Payment } from '../entities/payment'
import { beforeHookException, throwExceptionId } from '../constants'

@ReadModel({
  authorize: 'all',
  before: [CartReadModel.beforeFn, CartReadModel.beforeFnV2],
})
export class CartReadModel {
  public constructor(
    readonly id: UUID,
    readonly cartItems: Array<CartItem>,
    readonly checks: number,
    public shippingAddress?: Address,
    public payment?: Payment,
    public cartItemsIds?: Array<string>
  ) {}

  public getChecks() {
    return this.checks
  }

  public static beforeFn(filter: FilterFor<CartReadModel>, currentUser?: UserEnvelope): FilterFor<CartReadModel> {
    if (filter.id && filter.id.eq === throwExceptionId) throw new Error(beforeHookException)
    return filter
  }

  public static beforeFnV2(filter: FilterFor<CartReadModel>, currentUser?: UserEnvelope): FilterFor<CartReadModel> {
    if (!filter.id || filter.id?.eq !== 'before-fn-test') return filter
    return { id: { eq: filter.id.eq + '-modified' } } as FilterFor<CartReadModel>
  }

  @Projects(Cart, 'id')
  public static updateWithCart(cart: Cart, oldCartReadModel?: CartReadModel): ProjectionResult<CartReadModel> {
    const cartProductIds = cart?.cartItems.map((item) => item.productId as string)
    // This method calls are here to ensure they work. More info: https://github.com/boostercloud/booster/issues/797
    cart.getId()
    if (oldCartReadModel) {
      oldCartReadModel.getChecks()
    }

    return new CartReadModel(
      cart.id,
      cart.cartItems,
      cart.checks,
      cart.shippingAddress,
      oldCartReadModel?.payment,
      cartProductIds
    )
  }

  @Projects(Payment, 'cartId')
  public static updateCartPaymentStatus(
    payment: Payment,
    oldCartReadModel?: CartReadModel
  ): ProjectionResult<CartReadModel> {
    if (!oldCartReadModel) {
      return new CartReadModel(payment.cartId, [], 0, undefined, payment)
    }

    return new CartReadModel(
      oldCartReadModel.id,
      oldCartReadModel.cartItems,
      oldCartReadModel.checks,
      oldCartReadModel.shippingAddress,
      payment
    )
  }
}
