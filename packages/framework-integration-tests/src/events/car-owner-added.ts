import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CarOwnerAdded {
  public constructor(readonly id: UUID, readonly name: string) {}

  public entityID(): UUID {
    return this.id
  }
}
