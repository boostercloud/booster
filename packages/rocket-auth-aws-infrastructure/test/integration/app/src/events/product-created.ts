import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { SKU } from '../common/sku'
import { Money } from '../common/money'

@Event
export class ProductCreated {
  public constructor(
    readonly productId: UUID,
    readonly sku: SKU,
    readonly displayName: string,
    readonly description: string,
    readonly price: Money
  ) {}

  public entityID(): UUID {
    return this.productId
  }
}
