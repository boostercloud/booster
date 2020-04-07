import { ProductUpdateReason } from '../../../events/product-updated'
import { UpdateProduct } from '../../../commands/update-product'
import { SKU } from '../../../common/sku'
import { Money } from '../../../common/money'
import { Picture } from '../../../common/picture'
import { UUID } from '@boostercloud/framework-types'

export class UpdateProductV1 {
  public constructor(
    readonly id: UUID,
    readonly sku: SKU,
    readonly name: string,
    readonly description: string,
    readonly price: Money,
    readonly pictures: Array<Picture>,
    readonly deleted: boolean = false,
    readonly reason: ProductUpdateReason = ProductUpdateReason.CatalogChange
  ) {}
}

// Current version
export class UpdateProductV2 extends UpdateProduct {}
