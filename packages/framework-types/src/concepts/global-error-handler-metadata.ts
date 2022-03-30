import { AnyClass } from '../typelevel'
import { CommandEnvelope } from '../envelope'
import { EventInterface } from './event'
import { ReadModelInterface } from './read-model'
import { EntityInterface } from './entity'

export interface GlobalErrorHandlerInterface extends AnyClass {
  onCommandHandlerError?(error: Error, command: CommandEnvelope): Promise<Error>
  onScheduledCommandHandlerError?(error: Error): Promise<Error>
  onDispatchEventHandlerError?(error: Error, eventInstance: EventInterface): Promise<Error>
  onReducerError?(error: Error, eventInstance: EventInterface, snapshotInstance: EntityInterface | null): Promise<Error>
  onProjectionError?(error: Error, entity: EntityInterface, readModel: ReadModelInterface | undefined): Promise<Error>
  onError?(error: Error): Promise<Error>
}

export interface GlobalErrorHandlerMetadata {
  readonly class: GlobalErrorHandlerInterface
}
