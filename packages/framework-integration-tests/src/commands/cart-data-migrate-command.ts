import { Booster, Command } from '@boostercloud/framework-core'
import { PaginatedEntitiesIdsResult, Register, UUID } from '@boostercloud/framework-types'
import { Cart } from '../entities/cart'
import { QUANTITY_AFTER_DATA_MIGRATION, QUANTITY_TO_MIGRATE_DATA } from '../constants'

@Command({
  authorize: 'all',
})
export class CartDataMigrateCommand {
  public constructor() {}

  public static async handle(_command: CartDataMigrateCommand, _register: Register): Promise<Array<UUID>> {
    console.log('migrating')
    const entitiesIdsResult = (await Booster.entitiesIDs('Cart', 50, undefined)) as PaginatedEntitiesIdsResult
    const paginatedEntityIdResults = entitiesIdsResult.items

    const carts = await Promise.all(
      paginatedEntityIdResults.map(async (entity) => await Booster.entity(Cart, entity.entityID))
    )
    const validCarts = carts.filter(
      (cart) =>
        cart &&
        cart?.cartItems &&
        cart.cartItems.length === 1 &&
        cart.cartItems[0].quantity === QUANTITY_TO_MIGRATE_DATA
    )
    if (!validCarts || validCarts.length === 0) {
      return []
    }

    return await Promise.all(
      validCarts.map(async (cart) => {
        const validCart = cart!
        validCart.cartItems[0].quantity = QUANTITY_AFTER_DATA_MIGRATION
        const newCart = new Cart(validCart.id, validCart.cartItems, validCart.shippingAddress, validCart.checks)
        await Booster.migrateEntity(Cart, validCart.id, newCart)
        console.log('Migrated', newCart)
        return validCart.id
      })
    )
  }
}
