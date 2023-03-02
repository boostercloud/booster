import {
  Booster,
  BoosterDataMigrationFinished,
  BoosterDataMigrations,
  DataMigration,
} from '@boostercloud/framework-core'
import { Cart } from '../../entities/cart'
import { Register } from '@boostercloud/framework-types'
import { QUANTITY_AFTER_DATA_MIGRATION_V1, QUANTITY_TO_MIGRATE_DATA } from '../../constants'

@DataMigration({
  order: 1,
})
export class CartIdDataMigrateV1 {
  public constructor() {}

  public static async start(register: Register): Promise<void> {
    console.log('Data migration')
    const entitiesIdsResult = await Booster.entitiesIDs('Cart', 500, undefined)
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
      return
    }
    await Promise.all(
      validCarts.map(async (cart) => {
        const validCart = cart!
        validCart.cartItems[0].quantity = QUANTITY_AFTER_DATA_MIGRATION_V1
        const newCart = new Cart(validCart.id, validCart.cartItems, validCart.shippingAddress, validCart.checks)
        await BoosterDataMigrations.migrateEntity('Cart', validCart.id, newCart)
        console.log('Migrated', newCart)
        return validCart.id
      })
    )

    register.events(new BoosterDataMigrationFinished('CartIdDataMigrateV1'))
  }
}
