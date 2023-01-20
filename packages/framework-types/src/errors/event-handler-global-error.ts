import { GlobalErrorContainer } from './global-error-container'
import { EventInterface } from '../concepts'

export class EventHandlerGlobalError extends GlobalErrorContainer {
  constructor(readonly eventInstance: EventInterface, originalError: Error) {
    super(originalError)
  }
}
