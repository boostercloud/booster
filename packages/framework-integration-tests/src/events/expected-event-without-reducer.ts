import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class ExpectedEventWithoutReducer {
  public constructor(readonly expectedEventWithoutReducer: UUID, readonly name: string) {}

  public entityID(): UUID {
    return this.expectedEventWithoutReducer
  }
}
