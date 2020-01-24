import '../common/address'
import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { ChangedCartItem } from '../events/changed-cart-item'
import { UpdatedShippingAddress as UpdatedCartShippingAddress } from '../events/updated-shipping-address'
import { CartPaid } from '../events/cart-paid'
import { Address } from '../common/address'
import { CartItem } from '../common/cart-item'

/**
 * A cart is a temporary object where users accumulate all the items they want to buy
 */
@Entity
export class Cart {
  public constructor(
    readonly id: UUID,
    readonly cartItems: Array<CartItem>,
    public shippingAddress?: Address,
    public paid: boolean = false
  ) {}

  @Reduces(ChangedCartItem)
  public static changeItem(event: ChangedCartItem, currentCart: Cart): Cart {
    if (currentCart == null) {
      currentCart = new Cart(event.cartId, [])
    }

    const current = currentCart.cartItems.find((cartItem: CartItem): boolean => cartItem.productId === event.productId)
    if (current) {
      current.quantity += event.quantity
      return currentCart
    } else {
      currentCart.cartItems.push({
        productId: event.productId,
        quantity: event.quantity,
      })
      return currentCart
    }
  }

  @Reduces(UpdatedCartShippingAddress)
  public static updatedShippingAddress(event: UpdatedCartShippingAddress, currentCart: Cart): Cart {
    if (currentCart == null) {
      currentCart = new Cart(event.cartId, [])
    }

    currentCart.shippingAddress = event.address
    return currentCart
  }

  @Reduces(CartPaid)
  public static pay(_event: CartPaid, currentCart: Cart): Cart {
    currentCart.paid = true
    return currentCart
  }
}
