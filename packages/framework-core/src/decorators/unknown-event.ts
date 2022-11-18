import { EventInterface, Class, UnknownEventMetadata } from '@boostercloud/framework-types'
import { Booster } from '../booster'

export function UnknownEvent<TEvent extends EventInterface>(): <TEntity>(
  eventClass: Class<unknown>,
  methodName: string,
  methodDescriptor: EventMethod<TEvent>
) => void {
  return (eventClass, methodName) => {
    registerEventUnknown({
      class: eventClass,
      methodName: methodName,
    })
  }
}

function registerEventUnknown(eventMetadata: UnknownEventMetadata): void {
  Booster.configureCurrentEnv((config): void => {
    const unknownEvent = config.unknownEvent
    if (unknownEvent) {
      throw new Error(
        `Error registering unknownEvent: The Unknown event was already registered by ${unknownEvent}.
        If you think that this is an error, try performing a clean build.`
      )
    }

    config.unknownEvent = eventMetadata
  })
}

type EventMethod<TEvent> = TypedPropertyDescriptor<(event: TEvent) => void>
