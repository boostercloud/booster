import { ReadModel, Projects } from '@boostercloud/framework-core'
import { SuperUser, UserWithEmail } from '../roles'
import { deleteReadModel, ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Product } from '../entities/Product'
import { SKU } from '../common/sku'
import { Money } from '../common/money'

// This is an example read model for a possible admin-exclusive report to show last and previous updates to products
@ReadModel({
  authorize: [UserWithEmail, SuperUser],
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
      return deleteReadModel
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
