import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class PackCreated {
  public constructor(readonly packID: UUID, readonly name: string, readonly products: Array<UUID>) {}

  public entityID(): UUID {
    return this.packID
  }
}
