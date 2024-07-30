import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CarModelAdded {
  public constructor(readonly id: UUID, readonly name: string, readonly brand: string) {}

  public entityID(): UUID {
    return this.id
  }
}
