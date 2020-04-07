import { UUID } from '@boostercloud/framework-types'
import { ProductUpdated } from '../../../events/product-updated'
import { ProductUpdateReason } from '../../../events/product-updated'
import { SKU } from '../../../common/sku'
import { Money } from '../../../common/money'
import { Picture } from '../../../common/picture'

// In the beginning the event "ProductUpdated" depended on the entity "Product". As this is a bad practice, we added a
// migration to change that. However, previous migrations still expects the field "product" in the event so we create
// this schema to keep everything compiling and avoid depending on the Product entity
interface Product {
  readonly id: UUID
  readonly sku: SKU
  readonly name: string
  readonly description: string
  readonly price: Money
  readonly pictures: Array<Picture>
  readonly deleted: boolean
}

export class ProductUpdatedV1 {
  public constructor(readonly product: Product) {}

  public entityID(): UUID {
    return this.product.id
  }
}

export class ProductUpdatedV2 {
  public constructor(readonly product: Product, readonly reason: string) {}

  public entityID(): UUID {
    return this.product.id
  }
}

export class ProductUpdatedV3 {
  public constructor(readonly product: Product, readonly reason: ProductUpdateReason) {}

  public entityID(): UUID {
    return this.product.id
  }
}
// Current version
export class ProductUpdatedV4 extends ProductUpdated {}
