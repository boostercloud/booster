import { Booster, BoosterEntityTouchFinished, BoosterTouchEntityHandler } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { TouchEntity } from '@boostercloud/framework-core/dist/decorators/touch-entity'
import { Cart } from '../entities/cart'

@TouchEntity({
  order: 1,
})
export class CartTouchV1 {
  public constructor() {}

  public static async start(register: Register): Promise<void> {
    console.log('Touch entity')
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

    register.events(new BoosterEntityTouchFinished('CartTouchV1'))
  }
}
