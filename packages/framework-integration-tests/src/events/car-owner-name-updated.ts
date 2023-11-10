import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CarOwnerNameUpdated {
  public constructor(readonly carOwnerId: UUID, readonly newOwnerName: string) {}

  public entityID(): UUID {
    return this.carOwnerId
  }
}
