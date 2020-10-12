import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class PostCreated {
  public constructor(
    readonly postId: UUID,
    readonly title: string,
    readonly body: string,
  ) {}

  public entityID(): UUID {
    return /* the associated entity ID */
  }
}
