import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CartChangedWithFields {
  public constructor(
    readonly cartId: UUID,
    readonly sku: string,
    readonly quantity: number,
  ) {}

  public entityID(): UUID {
    return /* the associated entity ID */
  }
}
