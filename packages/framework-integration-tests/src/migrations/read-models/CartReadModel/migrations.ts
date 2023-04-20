import { CartReadModelV1, CartReadModelV2 } from './schema-versions'
import { SchemaMigration, ToVersion } from '@boostercloud/framework-core'
import { CartReadModel } from '../../../read-models/cart-read-model'

@SchemaMigration(CartReadModel)
export class CartReadModelMigration {
  @ToVersion(2, { fromSchema: CartReadModelV1, toSchema: CartReadModelV2 })
  public async splitDescriptionFieldIntoShortAndLong(old: CartReadModelV1): Promise<CartReadModelV2> {
    return new CartReadModelV2(old.id, old.cartItems, 0, old.shippingAddress, old.payment, old.cartItemsIds)
  }
}
