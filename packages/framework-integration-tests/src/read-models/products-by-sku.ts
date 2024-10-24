import { ReadModel, Projects } from '@boostercloud/framework-core'
import {
  UUID,
  ProjectionResult,
  ProjectionInfoReason,
  ReadModelAction,
  ProjectionInfo,
} from '@boostercloud/framework-types'
import { Product } from '../entities/product'

interface ProductIDWithPrice {
  productId: UUID
  priceCents: number
}

@ReadModel({
  authorize: 'all',
})
export class ProductsBySKU {
  public constructor(
    public id: UUID,
    readonly products: Array<ProductIDWithPrice> = [],
    readonly firstProduct?: ProductIDWithPrice,
    readonly record?: Record<string, number>
  ) {}

  @Projects(Product, 'sku', ProductsBySKU.projectProduct)
  public static projectProduct(
    entity: Product,
    currentProductsBySKU?: ProductsBySKU,
    projectionInfo?: ProjectionInfo
  ): ProjectionResult<ProductsBySKU> {
    if (projectionInfo?.reason === ProjectionInfoReason.ENTITY_DELETED) {
      return ReadModelAction.Delete
    }
    // The purpose of this projection is to test the Optimistic concurrency. We have a read model that accumulates entities
    // with different IDs. One instance of this read model (with id == product sku) will be updated when multiple instances
    // of the product entity (with the same SKU but different id) are changed.
    // This scenario will force optimistic concurrency issues to appear when combined with high contention load tests
    if (!currentProductsBySKU) {
      currentProductsBySKU = new ProductsBySKU(entity.sku, [])
    }

    const newAndSortedProducts = currentProductsBySKU.products
      .filter((pp) => pp.productId != entity.id) // Remove the product if it was present
      .concat({ productId: entity.id, priceCents: entity.price.cents }) // Add the new product or the existing, removed, product with the latest information
      .sort((a, b) => b.priceCents - a.priceCents)

    return new ProductsBySKU(currentProductsBySKU.id, newAndSortedProducts, newAndSortedProducts[0], {
      items: newAndSortedProducts.length,
    })
  }
}
