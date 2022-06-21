import { Address } from '../common/address'
import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/cart-item-changed'
import { ShippingAddressUpdated as UpdatedCartShippingAddress } from '../events/shipping-address-updated'

import { CartItem } from '../common/cart-item'
import { CartChecked } from '../events/cart-checked'
import { beforeHookProductId, reducerErrorCartId, reducerErrorCartMessage } from '../constants'

/**
 * A cart is a temporary object where users accumulate all the items they want to buy
 */
@Entity({
  authorizeReadEvents: 'all',
})
export class Cart {
  public constructor(
    readonly id: UUID,
    readonly cartItems: Array<CartItem>,
    public shippingAddress?: Address,
    public checks = 0
  ) {}
  public getId() {
    return this.id
  }
  @Reduces(CartItemChanged)
  public static changeItem(event: CartItemChanged, currentCart: Cart): Cart {
    if (event.cartId === reducerErrorCartId) {
      throw new Error(reducerErrorCartMessage)
    }
    if (currentCart == null) {
      currentCart = new Cart(event.cartId, [])
    }
    // This method calls are here to ensure they work. More info: https://github.com/boostercloud/booster/issues/797
    currentCart.getId()
    event.getProductId()

    const current = currentCart.cartItems.find((cartItem: CartItem): boolean => cartItem.productId === event.productId)
    // We don't want to increase quantity for the read models' before hook test as the cartId and productId are not random
    // and executing integration tests is failing when running them more than once.
    if (current && current.productId !== beforeHookProductId) {
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

  @Reduces(CartChecked)
  public static checkCart(event: CartChecked, currentCart: Cart): Cart {
    if (currentCart == null) {
      currentCart = new Cart(event.cartId, [])
    }

    currentCart.checks += 1
    return currentCart
  }
}

@Entity({
  authorizeReadEvents: 'all',
})
export class MigratedCart extends Cart {}
