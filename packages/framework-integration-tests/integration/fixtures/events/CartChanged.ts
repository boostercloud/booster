import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CartChanged {
  public constructor(
  ) {}

  public entityID(): UUID {
    return /* the associated entity ID */
  }
}
