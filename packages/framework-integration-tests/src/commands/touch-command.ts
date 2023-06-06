import { Booster, BoosterTouchEntityHandler, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { Cart } from '../entities/cart'

@Command({
  authorize: 'all',
})
export class TouchCommand {
  public constructor() {}

  public static async handle(_command: TouchCommand, _register: Register): Promise<void> {
    const entitiesIdsResult = await Booster.entitiesIDs('Cart', 500, undefined)
    const paginatedEntityIdResults = entitiesIdsResult.items
    const carts = await Promise.all(
      paginatedEntityIdResults.map(async (entity) => await Booster.entity(Cart, entity.entityID))
    )
    if (!carts || carts.length === 0) {
      return
    }
    await Promise.all(
      carts.map(async (cart) => {
        const validCart = cart!
        await BoosterTouchEntityHandler.touchEntity('Cart', validCart.id)
        console.log('Touched', validCart)
        return validCart.id
      })
    )
  }
}
