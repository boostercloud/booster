import { Projects, ReadModel } from '@boostercloud/framework-core'
import { UserWithEmail } from '../roles'
import { ProjectionResult, ReadModelAction, UUID } from '@boostercloud/framework-types'
import { Product } from '../entities/product'
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
  public static updateWithProduct(product: Product): ProjectionResult<ProductReadModel> {
    if (product.deleted) {
      return ReadModelAction.Delete
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
