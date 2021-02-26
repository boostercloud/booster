import { Entity, Reduces } from '@boostercloud/framework-core'
import { SKU } from '../common/sku'
import { ProductCreated } from '../events/product-created'
import { ProductUpdated } from '../events/product-updated'
import { ProductDeleted } from '../events/product-deleted'
import { Money } from '../common/money'
import { UUID } from '@boostercloud/framework-types'
import { Picture } from '../common/picture'
import { ProductAvailabilityChanged } from '../events/product-availability-changed'

/**
 * A product is the representation of a sellable unit in our sample store
 */
@Entity
export class Product {
  public constructor(
    public id: UUID,
    readonly sku: SKU,
    readonly displayName: string,
    readonly description: string,
    readonly price: Money,
    readonly pictures: Array<Picture>,
    public deleted: boolean = false,
    public availability: number = 0
  ) {}

  @Reduces(ProductCreated)
  public static create(event: ProductCreated): Product {
    return new Product(event.productId, event.sku, event.displayName, event.description, event.price, [])
  }

  @Reduces(ProductUpdated)
  public static update(event: ProductUpdated): Product {
    return new Product(event.id, event.sku, event.name, event.description, event.price, event.pictures, event.deleted)
  }

  @Reduces(ProductDeleted)
  public static delete(_event: ProductDeleted, currentProduct: Product): Product {
    if (currentProduct) {
      currentProduct.deleted = true
    }
    return currentProduct
  }

  @Reduces(ProductAvailabilityChanged)
  public static changeAvailability(event: ProductAvailabilityChanged, currentProduct: Product): Product {
    if (currentProduct) {
      currentProduct.availability += event.quantity
    }
    return currentProduct
  }
}
