import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class AnotherCounterAdded {
  public constructor(readonly anotherCounterId: UUID, readonly identifier: string) {}

  public entityID(): UUID {
    return this.anotherCounterId
  }
}
