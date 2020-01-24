import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class ProductDeleted {
  public constructor(readonly productId: UUID) {}

  public entityID(): string {
    return this.productId
  }
}
