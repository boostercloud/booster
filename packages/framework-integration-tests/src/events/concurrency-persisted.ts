import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class ConcurrencyPersisted {
  public constructor(readonly id: UUID, readonly otherId: UUID) {}

  public entityID(): UUID {
    return this.id
  }
}
