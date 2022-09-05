import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { PromotionCreated } from '../events/promotion-created'

@Command({
  authorize: 'all',
})
export class CreatePromotions {
  public constructor(
    readonly promotionID: UUID,
    readonly name: string,
    readonly productID: UUID,
    readonly promotionType: 'Product' | 'Other'
  ) {}

  public static async handle(command: CreatePromotions, register: Register): Promise<void> {
    const promotionID = command.promotionID ?? UUID.generate()
    register.events(new PromotionCreated(promotionID, command.name, command.productID, command.promotionType))
  }
}
