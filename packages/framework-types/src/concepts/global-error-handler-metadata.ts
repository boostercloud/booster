import { AnyClass } from '../typelevel'
import { CommandEnvelope } from '../envelope'
import { EventInterface } from './event'
import { ReadModelInterface } from './read-model'
import { EntityInterface } from './entity'

export interface GlobalErrorHandlerInterface extends AnyClass {
  onCommandHandlerError?(error: Error, command: CommandEnvelope): Promise<Error | undefined>
  onScheduledCommandHandlerError?(error: Error): Promise<Error | undefined>
  onDispatchEventHandlerError?(error: Error, eventInstance: EventInterface): Promise<Error | undefined>
  onReducerError?(
    error: Error,
    eventInstance: EventInterface,
    snapshotInstance: EntityInterface | undefined
  ): Promise<Error | undefined>
  onProjectionError?(
    error: Error,
    entity: EntityInterface,
    readModel: ReadModelInterface | undefined
  ): Promise<Error | undefined>
  onError?(error: Error | undefined): Promise<Error | undefined>
}

export interface GlobalErrorHandlerMetadata {
  readonly class: GlobalErrorHandlerInterface
}
