import { GlobalErrorContainer } from './global-error-container'
import { EntityInterface, EventInterface } from '../concepts'

export class ReducerGlobalError extends GlobalErrorContainer {
  constructor(
    readonly eventInstance: EventInterface,
    readonly snapshotInstance: EntityInterface | null,
    originalError: Error
  ) {
    super(originalError)
  }
}
