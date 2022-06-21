import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CounterAdded {
  public constructor(readonly counterId: UUID, readonly identifier: string) {}

  public entityID(): UUID {
    return this.counterId
  }
}
