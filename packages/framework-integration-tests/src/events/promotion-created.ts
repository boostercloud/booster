import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class PromotionCreated {
  public constructor(
    readonly promotionID: UUID,
    readonly name: string,
    readonly productID: UUID,
    readonly promotionType: 'Product' | 'Other'
  ) {}

  public entityID(): UUID {
    return this.promotionID
  }
}
