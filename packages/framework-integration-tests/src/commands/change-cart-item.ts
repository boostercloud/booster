import { Booster, Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CartItemChanged } from '../events/cart-item-changed'
import { Cart } from '../entities/cart'

@Command({
  authorize: 'all',
})
export class ChangeCartItem {
  public constructor(readonly cartId: UUID, readonly productId: UUID, readonly quantity: number) {}

  public static async handle(command: ChangeCartItem, register: Register): Promise<void> {
    const newestSnapshot = await Booster.entity(Cart, 'the-checked-cart')
    console.log('///// NEWEST SNAPSHOT /////')
    console.log(newestSnapshot)
    const oldSnapshot = await Booster.entity(Cart, 'the-checked-cart', new Date('2021-03-24T10:59:05.606Z'))
    console.log('///// OLDEST SNAPSHOT /////')
    console.log(oldSnapshot)
    register.events(new CartItemChanged(command.cartId, command.productId, command.quantity))
  }
}
