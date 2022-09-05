import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { PromotionCreated } from '../events/promotion-created'

@Entity
export class Promotion {
  public constructor(
    readonly id: UUID,
    readonly name: string,
    readonly productID: UUID,
    readonly promotionType: 'Product' | 'Other'
  ) {}

  @Reduces(PromotionCreated)
  public static createPromotion(event: PromotionCreated): Promotion {
    return new Promotion(event.promotionID, event.name, event.productID, event.promotionType)
  }
}
