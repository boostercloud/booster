import { GlobalErrorContainer } from './global-error-container'
import { EventEnvelope } from '../envelope'

export class EventGlobalError extends GlobalErrorContainer {
  constructor(readonly eventEnvelope: EventEnvelope, originalError: Error) {
    super(originalError)
  }
}
