import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class BookAdded {
  public constructor(readonly id: UUID, readonly title: string, readonly pages: number) {}

  public entityID(): UUID {
    return this.id
  }
}
