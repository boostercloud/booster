import { EventInterface } from '@boostercloud/framework-types'
import { UnexpectedEventHandled } from '../commands/unexpected-events-command'
import { UnknownEvent, UnknownReducer } from '@boostercloud/framework-core'
import { ExpectedEventWithoutReducer } from '../events/expected-event-without-reducer'

export class UnknownEvents {
  @UnknownEvent()
  public static unknownEvent(event: EventInterface): void {
    // Return void for UnexpectedEventHandled. No entities will be created and the system will not fail
    if ((event as UnexpectedEventHandled).unexpectedEventHandledId !== undefined) {
      return
    }
    throw Error('Unexpected event captured with UnknownEvent')
  }

  @UnknownReducer()
  public static unknownReducer(event: EventInterface): void {
    // Return void for UnexpectedEventWithoutReducer. No entities will be created and the system will not fail
    if ((event as ExpectedEventWithoutReducer).expectedEventWithoutReducer !== undefined) {
      return
    }
    throw Error('Unexpected event captured with UnknownReducer')
  }
}
