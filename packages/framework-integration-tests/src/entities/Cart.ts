import '../common/address'
import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/CartItemChanged'
import { ShippingAddressUpdated as UpdatedCartShippingAddress } from '../events/ShippingAddressUpdated'
import { Address } from '../common/address'
import { CartItem } from '../common/cart-item'

/**
 * A cart is a temporary object where users accumulate all the items they want to buy
 */
@Entity
export class Cart {
  public constructor(readonly id: UUID, readonly cartItems: Array<CartItem>, public shippingAddress?: Address) {}

  @Reduces(CartItemChanged)
  public static changeItem(event: CartItemChanged, currentCart: Cart): Cart {
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
}
