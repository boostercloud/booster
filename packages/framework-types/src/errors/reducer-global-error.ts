import { GlobalErrorContainer } from './global-error-container'
import { EntityInterface, EventInterface, ReducerMetadata } from '../concepts'
import { EventEnvelope } from '../envelope'

export class ReducerGlobalError extends GlobalErrorContainer {
  constructor(
    readonly eventEnvelope: EventEnvelope,
    readonly eventInstance: EventInterface,
    readonly snapshotInstance: EntityInterface | null,
    readonly reducerMetadata: ReducerMetadata,
    originalError: Error
  ) {
    super(originalError)
  }
}
