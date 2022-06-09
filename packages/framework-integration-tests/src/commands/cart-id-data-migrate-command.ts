import { Booster, Command } from '@boostercloud/framework-core'
import { PaginatedEntitiesIdsResult, Register, UUID } from '@boostercloud/framework-types'
import { Cart } from '../entities/cart'
import { QUANTITY_AFTER_DATA_MIGRATION_ID, QUANTITY_TO_MIGRATE_ID } from '../constants'

@Command({
  authorize: 'all',
})
export class CartIdDataMigrateCommand {
  public constructor() {}

  public static async handle(_command: CartIdDataMigrateCommand, _register: Register): Promise<Array<UUID>> {
    const entitiesIdsResult = (await Booster.entitiesIDs('Cart', 50, undefined)) as PaginatedEntitiesIdsResult
    const paginatedEntityIdResults = entitiesIdsResult.items
    const carts = await Promise.all(
      paginatedEntityIdResults.map(async (entity) => await Booster.entity(Cart, entity.entityID))
    )
    const validCarts = carts.filter(
      (cart) =>
        cart && cart?.cartItems && cart.cartItems.length === 1 && cart.cartItems[0].quantity === QUANTITY_TO_MIGRATE_ID
    )
    if (!validCarts || validCarts.length === 0) {
      return []
    }
    return await Promise.all(
      validCarts.map(async (cart) => {
        const validCart = cart!
        const newCartId = UUID.generate()
        validCart.cartItems[0].quantity = QUANTITY_AFTER_DATA_MIGRATION_ID
        const newCart = new Cart(newCartId, validCart.cartItems, validCart.shippingAddress, validCart.checks)
        await Booster.migrateEntity(Cart, validCart.id, newCart)
        console.log('Migrated', newCart)
        return newCart.id
      })
    )
  }
}
