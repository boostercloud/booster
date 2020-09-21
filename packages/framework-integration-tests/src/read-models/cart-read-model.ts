import { ReadModel, Projects } from '@boostercloud/framework-core'
import { UUID, ProjectionResult } from '@boostercloud/framework-types'
import { CartItem } from '../common/cart-item'
import { Address } from '../common/address'
import { Cart } from '../entities/Cart'
import { Payment } from '../entities/Payment'

@ReadModel({
  authorize: 'all',
})
export class CartReadModel {
  public constructor(
    readonly id: UUID,
    readonly cartItems: Array<CartItem>,
    public shippingAddress?: Address,
    public payment?: Payment
  ) {}

  @Projects(Cart, 'id')
  public static updateWithCart(cart: Cart, oldCartReadModel?: CartReadModel): ProjectionResult<CartReadModel> {
    return new CartReadModel(cart.id, cart.cartItems, cart.shippingAddress, oldCartReadModel?.payment)
  }

  @Projects(Payment, 'cartId')
  public static updateCartPaymentStatus(
    payment: Payment,
    oldCartReadModel?: CartReadModel
  ): ProjectionResult<CartReadModel> {
    if (!oldCartReadModel) {
      return new CartReadModel(payment.cartId, [], undefined, payment)
    }

    return new CartReadModel(oldCartReadModel.id, oldCartReadModel.cartItems, oldCartReadModel.shippingAddress, payment)
  }
}
