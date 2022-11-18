import { Command } from '@boostercloud/framework-core'
import { EventInterface, Register, UUID } from '@boostercloud/framework-types'
import { UnexpectedEventWithoutReducer } from '../events/unexpected-event-without-reducer'
import { ExpectedEventWithoutReducer } from '../events/expected-event-without-reducer'

@Command({
  authorize: 'all',
})
export class UnexpectedEventsCommand {
  public constructor(
    readonly type:
      | 'UNEXPECTED_EVENT_WITHOUT_REDUCER'
      | 'EXPECTED_EVENT_WITHOUT_REDUCER'
      | 'UNEXPECTED_EVENT_HANDLED'
      | 'EXPECTED_EVENT_UNHANDLED'
  ) {}

  public static async handle(command: UnexpectedEventsCommand, register: Register): Promise<void> {
    switch (command.type) {
      case 'UNEXPECTED_EVENT_WITHOUT_REDUCER': // Generates an event that is unhandled and should throw an exception
        register.events(new UnexpectedEventWithoutReducer(UUID.generate()))
        return
      case 'EXPECTED_EVENT_WITHOUT_REDUCER': // Generates an event that is handled by the unknownReducer and should be ignored
        register.events(new ExpectedEventWithoutReducer(UUID.generate(), 'EXPECTED_EVENT_WITHOUT_REDUCER'))
        return
      case 'UNEXPECTED_EVENT_HANDLED': // Generates an event that is not registered and should throw an exception
        register.events(new UnexpectedEventHandled(UUID.generate()))
        return
      case 'EXPECTED_EVENT_UNHANDLED': // Generates an event that is handled by the unknownEvent and should be ignored
        register.events(new ExpectedEventUnHandled(UUID.generate()))
        return
    }
    throw new Error('Unexpected value')
  }
}

export class ExpectedEventUnHandled implements EventInterface {
  public constructor(readonly expectedEventUnHandledId: UUID) {}

  public entityID(): UUID {
    return this.expectedEventUnHandledId
  }
}

export class UnexpectedEventHandled implements EventInterface {
  public constructor(readonly unexpectedEventHandledId: UUID) {}

  public entityID(): UUID {
    return this.unexpectedEventHandledId
  }
}
