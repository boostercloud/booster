import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { Money } from '../common/money'
import { Picture } from '../common/picture'

export enum ProductUpdateReason {
  CatalogChange = 'CatalogChange',
  FixDescription = 'FixDescription',
  FixName = 'FixName',
}

@Event
export class ProductUpdated {
  public constructor(
    readonly id: UUID,
    readonly sku: string,
    readonly name: string,
    readonly description: string,
    readonly price: Money,
    readonly pictures: Array<Picture>,
    readonly deleted: boolean = false,
    readonly reason: ProductUpdateReason,
    readonly productDetails: Record<string, unknown> = {},
    readonly productType: ProductType = 'Other'
  ) {}

  public entityID(): UUID {
    return this.id
  }
}
