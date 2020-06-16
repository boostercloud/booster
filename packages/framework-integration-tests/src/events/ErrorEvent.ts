import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class ErrorEvent {
  public constructor(readonly id: UUID, readonly message: string, readonly payload: unknown) {}

  public entityID(): UUID {
    return this.id
  }
}
