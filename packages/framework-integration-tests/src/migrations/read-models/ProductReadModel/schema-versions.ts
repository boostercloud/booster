import { UUID } from '@boostercloud/framework-types'
import { ProductReadModel } from '../../../read-models/product-read-model'
import { Money } from '../../../common/money'
import { Picture } from '../../../common/picture'

export class ProductReadModelV1 {
  public constructor(
    public id: UUID,
    readonly sku: string,
    readonly name: string,
    readonly description: string,
    readonly price: Money,
    readonly pictures: Array<Picture>,
    public deleted: boolean = false
  ) {}
}

// Current version
export class ProductReadModelV2 extends ProductReadModel {}
