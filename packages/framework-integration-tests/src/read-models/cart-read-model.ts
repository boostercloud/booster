import { ReadModel, Projects } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CartItem } from '../common/cart-item'
import { Address } from '../common/address'
import { Cart } from '../entities/cart'

@ReadModel({
  authorize: 'all',
})
export class CartReadModel {
  public constructor(
    readonly id: UUID,
    readonly cartItems: Array<CartItem>,
    public paid: boolean,
    public somethingElse: boolean,
    public shippingAddress?: Address
  ) {}

  @Projects(Cart, 'id')
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  public static updateWithCart(cart: Cart, _oldCartReadModel?: CartReadModel): CartReadModel {
    return new CartReadModel(cart.id, cart.cartItems, cart.paid, false, cart.shippingAddress)
  }
}
