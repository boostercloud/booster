import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CarOfferAdded {
  public constructor(readonly id: UUID, readonly name: string, readonly purchasesIds: Array<string>) {}

  public entityID(): UUID {
    return this.id
  }
}
