import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class BookAdded {
  public constructor(readonly bookId: UUID, readonly title: string) {}

  public entityID(): UUID {
    return this.bookId
  }
}
