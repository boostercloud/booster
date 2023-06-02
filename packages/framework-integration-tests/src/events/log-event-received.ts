import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class LogEventReceived {
  public constructor(readonly id: UUID, readonly value: string) {}

  public entityID(): UUID {
    return this.id
  }
}
