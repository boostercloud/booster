import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class UnexpectedEventWithoutReducer {
  public constructor(readonly unexpectedEventWithoutReducer: UUID) {}

  public entityID(): UUID {
    return this.unexpectedEventWithoutReducer
  }
}
