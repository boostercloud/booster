import { ReadModel, Projects } from '@boostercloud/framework-core'
import { UserWithEmail } from '../roles'
import { UUID } from '@boostercloud/framework-types'
import { Product } from '../entities/Product'
import { SKU } from '../common/sku'
import { Money } from '../common/money'

// This is an example read model for a possible admin-exclusive report to show last and previous updates to products
@ReadModel({
  authorize: [UserWithEmail],
})
export class ProductReadModel {
  public constructor(
    readonly id: UUID,
    readonly sku: SKU,
    readonly displayName: string,
    readonly description: string,
    readonly availability: number,
    public deleted: boolean,
    readonly price?: Money
  ) {}

  @Projects(Product, 'id')
  public static updateWithProduct(product: Product): ProductReadModel {
    if (product.deleted) {
      // TODO: Consider solutions to delete read models from the database (see BOOST-587)
      return new ProductReadModel(product.id, '<DELETED>', '', '', 0, true)
    } else {
      return new ProductReadModel(
        product.id,
        product.sku,
        product.displayName,
        product.description,
        product.availability,
        product.deleted,
        product.price
      )
    }
  }
}
