import { Money } from '../../../common/money'
import { Picture } from '../../../common/picture'
import { UUID } from '@boostercloud/framework-types'
import { Product } from '../../../entities/product'

export class ProductV1 {
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

export class ProductV2 extends Product {}
