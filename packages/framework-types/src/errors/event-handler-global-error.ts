import { GlobalErrorContainer } from './global-error-container'
import { EventInterface, NotificationInterface } from '../concepts'
import { EventEnvelope } from '../envelope'

export class EventHandlerGlobalError extends GlobalErrorContainer {
  constructor(
    readonly eventEnvelope: EventEnvelope | NotificationInterface,
    readonly eventInstance: EventInterface,
    readonly eventHandlerMetadata: unknown,
    originalError: Error
  ) {
    super(originalError)
  }
}
