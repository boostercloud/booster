import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { Money } from '../common/money'

@Event
export class ProductCreated {
  public constructor(
    readonly productId: UUID,
    readonly sku: string,
    readonly displayName: string,
    readonly description: string,
    readonly price: Money
  ) {}

  public entityID(): UUID {
    return this.productId
  }
}
