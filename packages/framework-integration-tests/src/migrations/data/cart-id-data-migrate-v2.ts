import {
  Booster,
  BoosterDataMigrationFinished,
  BoosterDataMigrations,
  DataMigration,
} from '@boostercloud/framework-core'
import { Cart, MigratedCart } from '../../entities/cart'
import { Register } from '@boostercloud/framework-types'
import { NEW_CART_IDS, QUANTITY_AFTER_DATA_MIGRATION_V1, QUANTITY_AFTER_DATA_MIGRATION_V2 } from '../../constants'

@DataMigration({
  order: 2,
})
export class CartIdDataMigrateV2 {
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
        cart.cartItems[0].quantity === QUANTITY_AFTER_DATA_MIGRATION_V1
    )
    if (!validCarts || validCarts.length === 0) {
      return
    }
    let index = 0
    await Promise.all(
      validCarts.map(async (cart) => {
        const validCart = cart!
        const newCartId = NEW_CART_IDS[index]
        index++
        validCart.cartItems[0].quantity = QUANTITY_AFTER_DATA_MIGRATION_V2
        const newCart = new MigratedCart(newCartId, validCart.cartItems, validCart.shippingAddress, validCart.checks)
        await BoosterDataMigrations.migrateEntity('Cart', validCart.id, newCart)
        console.log('Migrated', newCart)
        return newCart.id
      })
    )

    register.events(new BoosterDataMigrationFinished('CartIdDataMigrateV2'))
  }
}
