import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { Money } from '../common/money'
import { ProductType } from '../entities/product'

@Event
export class ProductCreated {
  public constructor(
    readonly productId: UUID,
    readonly sku: string,
    readonly displayName: string,
    readonly description: string,
    readonly price: Money,
    readonly productDetails: Record<string, unknown> = {},
    readonly productType: ProductType = 'Other'
  ) {}

  public entityID(): UUID {
    return this.productId
  }
}
