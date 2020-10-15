import { ReadModel, Projects } from '@boostercloud/framework-core'
import { Admin } from '../roles'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { Product } from '../entities/product'

// This is an example read model for a possible admin-exclusive report to show last and previous updates to products
@ReadModel({
  authorize: [Admin],
})
export class ProductUpdatesReadModel {
  public constructor(
    readonly id: UUID,
    readonly availability: number,
    readonly lastUpdate: Date,
    readonly previousUpdate?: Date
  ) {}

  @Projects(Product, 'id')
  public static updateWithProduct(
    product: Product,
    previous?: ProductUpdatesReadModel
  ): ProjectionResult<ProductUpdatesReadModel> {
    return new ProductUpdatesReadModel(product.id, product.availability, new Date(), previous?.lastUpdate)
  }
}
