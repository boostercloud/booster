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
    const previousEvents = new Array(command.previousProducts).map(() => new CartAbandoned(command.cartId.toString()))

    const afterEvents = new Array(command.afterProducts).map(() => new CartAbandoned(command.cartId.toString()))

    register.events(...previousEvents)

    await register.flush()
    const previousCart = await FlushNotifications.getEntity(command.cartId)

    register.events(...afterEvents)
    const afterCart = await FlushNotifications.getEntity(command.cartId)
    return [previousCart, afterCart]
  }

  private static async getEntity(cartId: UUID, retries = 0): Promise<Cart> {
    const cart = await Booster.entity(Cart, cartId)
    if (cart || retries >= 10) {
      return cart as Cart
    }
    return new Promise((resolve) => {
      setTimeout(async () => resolve(await FlushNotifications.getEntity(cartId, retries + 1)), 1000)
    })
  }
}
