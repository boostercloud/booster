import { Booster, Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { Cart } from '../entities/cart'
import { CartAbandoned } from '../notifications/cart-abandoned'

@Command({
  authorize: 'all',
})
export class FlushNotifications {
  public constructor(readonly cartId: UUID, readonly previousProducts: number, readonly afterProducts: number) {}

  public static async handle(command: FlushNotifications, register: Register): Promise<Array<Cart>> {
    const previousEvents = []
    for (let i = 0; i < command.previousProducts; i++) {
      previousEvents.push(new CartAbandoned(command.cartId.toString()))
    }

    const afterEvents = []
    for (let i = 0; i < command.afterProducts; i++) {
      afterEvents.push(new CartAbandoned(command.cartId.toString()))
    }

    console.log('REGISTERING PREVIOUS EVENTS', previousEvents)
    register.events(...previousEvents)

    await register.flush()
    console.log('GETTING CART')
    const previousCart = await FlushNotifications.getCart(command.cartId)

    console.log('REGISTERING AFTER EVENTS', afterEvents)
    register.events(...afterEvents)

    console.log('GETTING SECOND CART')
    const afterCart = await FlushNotifications.getCart(command.cartId)
    return [previousCart, afterCart]
  }

  private static async getCart(cartId: UUID): Promise<Cart> {
    let maxRetries = 10
    let cart: Cart | undefined = undefined
    do {
      cart = await new Promise((resolve) =>
        setTimeout(() => resolve(Booster.entity(Cart, cartId)), 1000 * (10 - maxRetries))
      )
    } while (!cart && maxRetries-- > 0)
    if (!cart) {
      throw new Error('Could not get the cart')
    }
    return cart
  }
}
