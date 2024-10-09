import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class MovieAdded {
  public constructor(readonly movieId: UUID, readonly title: string) {}

  public entityID(): UUID {
    return this.movieId
  }
}
