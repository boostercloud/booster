import { GlobalErrorContainer } from './global-error-container'
import { EventEnvelope } from '../envelope'

export class SnapshotPersistHandlerGlobalError extends GlobalErrorContainer {
  constructor(readonly snapshot: EventEnvelope, originalError: Error) {
    super(originalError)
  }
}
